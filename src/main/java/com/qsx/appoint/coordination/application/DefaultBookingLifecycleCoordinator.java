package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.PaymentLifecycle;
import com.qsx.appoint.payment.application.PaymentLifecycleCase;
import com.qsx.appoint.payment.application.PaymentRefundOperations;
import com.qsx.appoint.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
class DefaultBookingLifecycleCoordinator implements BookingLifecycleCoordinator {
    private final BookingLifecyclePolicy policy = new BookingLifecyclePolicy();
    private final AppointmentLifecycle appointments;
    private final PaymentLifecycle payments;
    private final PaymentRefundOperations refunds;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public PaymentResponse simulatePaymentSuccess(Long userId, Long paymentId) {
        PaymentLifecycleCase lookupPayment = payments.find(paymentId);
        if (lookupPayment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        AppointmentLifecycleCase appointment = appointments.lock(lookupPayment.appointmentOrderId());
        if (appointment == null) throw integrityFailure("支付单关联的预约不存在", null, paymentId);
        PaymentLifecycleCase payment = payments.lockByAppointment(appointment.id());
        validateIntegrity(appointment, payment, paymentId);
        if (!appointment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);

        if (appointment.status() == AppointmentStatus.CONFIRMED && payment.status() == PaymentOrderStatus.PAID) {
            return response(payment, appointment);
        }
        if (appointment.status() == AppointmentStatus.EXPIRED) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        if (policy.inactive(appointment.status())) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        requirePendingUnpaid(appointment, payment, paymentId);

        LocalDateTime now = now();
        if (!now.isBefore(appointment.paymentExpiresAt())) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        var confirmation = appointments.confirm(appointment);
        payments.markPaid(payment.id());
        return new PaymentResponse(payment.id(), payment.paymentNo(), "PAID",
                appointment.id(), "CONFIRMED", confirmation.visitNumber());
    }

    @Override
    @Transactional
    public boolean cancelOwned(Long userId, Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!appointment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());

        LocalDateTime now = policy.inactive(appointment.status()) ? null : now();
        var decision = policy.decideCancellation(appointment,payment,now);
        return switch (decision.action()) {
            case NO_ACTION -> decision.refundPending();
            case CLOSE_UNPAID -> {
                closePending(appointment,payment,decision.closeReason(),now);
                yield false;
            }
            case CLOSE_PAID_AND_REFUND -> {
                closePaidPending(appointment,payment,decision.closeReason(),decision.refundReason(),now);
                yield true;
            }
            case CANCEL_CONFIRMED_AND_REFUND -> {
                refunds.requestFullRefund(payment.id(),decision.refundReason());
                appointments.cancelConfirmed(appointment,now);
                yield true;
            }
            case REJECT_DEADLINE -> throw new BusinessException(ErrorCode.CANCELLATION_DEADLINE_REACHED);
            case INVALID_STATE -> throw integrityFailure("预约与支付状态组合不一致",appointment.id(),payment.id());
        };
    }

    @Override
    @Transactional
    public boolean expireIfDue(Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) return false;
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT) return false;
        LocalDateTime now = now();
        if (now.isBefore(appointment.paymentExpiresAt())) return false;
        PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        return applyResolution(policy.decideExpiration(appointment,payment,now),appointment,payment,now);
    }

    @Override
    @Transactional
    public boolean reconcilePaidPending(Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) return false;
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT) return false;
        PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if (payment.status() != PaymentOrderStatus.PAID) return false;
        LocalDateTime now = now();
        return applyResolution(policy.decidePaidPending(appointment,payment),appointment,payment,now);
    }

    @Override
    @Transactional
    public boolean ensureRefundForClosedPaid(Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) return false;
        if (!policy.inactive(appointment.status())) return false;
        PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if (!policy.requiresClosedPaidRefund(appointment,payment)) return false;
        refunds.requestFullRefund(payment.id(), RefundReason.RECONCILIATION_CLOSED_PAID);
        return true;
    }

    private void closePending(AppointmentLifecycleCase appointment,
                              PaymentLifecycleCase payment, AppointmentCloseReason status, LocalDateTime now) {
        appointments.closePending(appointment, status, now);
        payments.closeUnpaid(payment);
        log.info("预约关闭，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.id(), payment.id(), status.status().name());
    }

    private void closePaidPending(AppointmentLifecycleCase appointment,
                                  PaymentLifecycleCase payment, AppointmentCloseReason status, RefundReason reason,
                                  LocalDateTime now) {
        refunds.requestFullRefund(payment.id(), reason);
        appointments.closePending(appointment, status, now);
        log.info("已支付待确认预约关闭并进入退款，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.id(), payment.id(), status.status().name());
    }

    private void requirePendingUnpaid(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment, Long paymentId) {
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT
                || !(payment.status() == PaymentOrderStatus.UNPAID || payment.status() == PaymentOrderStatus.PAYING)) {
            throw integrityFailure("预约与支付状态组合不一致", appointment.id(), paymentId);
        }
    }

    private boolean applyResolution(BookingLifecyclePolicy.ExpirationDecision decision,
                                    AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                                    LocalDateTime now) {
        return switch (decision) {
            case NO_ACTION -> false;
            case CONFIRM -> {
                appointments.confirm(appointment);
                log.info("对账发现已支付事实并恢复确认，appointmentOrderId={}, paymentOrderId={}",appointment.id(),payment.id());
                yield true;
            }
            case CLOSE_UNPAID -> {
                closePending(appointment,payment,AppointmentCloseReason.PAYMENT_EXPIRED,now);
                yield true;
            }
            case REFUND_LATE_PAYMENT -> {
                closePaidPending(appointment,payment,AppointmentCloseReason.PAYMENT_EXPIRED,
                        RefundReason.APPOINTMENT_NOT_CONFIRMABLE,now);
                yield true;
            }
            case INVALID_STATE -> throw integrityFailure("预约与支付状态组合不一致",appointment.id(),payment.id());
        };
    }

    private void validateIntegrity(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                                   Long expectedPaymentId) {
        if (payment == null) throw integrityFailure("预约关联的支付单不存在", appointment.id(), expectedPaymentId);
        if (expectedPaymentId != null && !payment.id().equals(expectedPaymentId)) {
            throw integrityFailure("支付单关联关系不一致", appointment.id(), expectedPaymentId);
        }
        if (!payment.appointmentOrderId().equals(appointment.id()) || !payment.userId().equals(appointment.userId())) {
            throw integrityFailure("预约与支付单归属不一致", appointment.id(), payment.id());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private PaymentResponse response(PaymentLifecycleCase payment, AppointmentLifecycleCase appointment) {
        return new PaymentResponse(payment.id(), payment.paymentNo(), payment.status().name(),
                appointment.id(), appointment.status().name(), appointment.visitNumber());
    }

    private BusinessException integrityFailure(String message, Long appointmentId, Long paymentId) {
        log.error("{}，appointmentOrderId={}, paymentOrderId={}", message, appointmentId, paymentId);
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

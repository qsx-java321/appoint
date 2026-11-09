package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import com.qsx.appoint.appointment.application.AppointmentLifecycleOperations;
import com.qsx.appoint.appointment.application.AppointmentLifecycleView;
import com.qsx.appoint.appointment.application.ScheduleLifecycleView;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.PaymentLifecycleOperations;
import com.qsx.appoint.payment.application.PaymentLifecycleView;
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
    private final AppointmentLifecycleOperations appointments;
    private final PaymentLifecycleOperations payments;
    private final PaymentRefundOperations refunds;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public PaymentResponse simulatePaymentSuccess(Long userId, Long paymentId) {
        PaymentLifecycleView lookupPayment = payments.findPayment(paymentId);
        if (lookupPayment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        AppointmentLifecycleView lookupAppointment = appointments.findAppointment(lookupPayment.appointmentOrderId());
        if (lookupAppointment == null) throw integrityFailure("支付单关联的预约不存在", null, paymentId);
        ScheduleLifecycleView schedule = appointments.lockSchedule(lookupAppointment.scheduleId());
        AppointmentLifecycleView appointment = appointments.lockAppointment(lookupAppointment.id());
        PaymentLifecycleView payment = payments.lockByAppointment(appointment.id());
        validateIntegrity(appointment, payment, paymentId);
        if (!appointment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);

        if (appointment.status() == AppointmentStatus.CONFIRMED && payment.status() == PaymentOrderStatus.PAID) {
            return response(payment, appointment);
        }
        if (appointment.status() == AppointmentStatus.EXPIRED) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        if (isInactive(appointment.status())) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        requirePendingUnpaid(appointment, payment, paymentId);

        LocalDateTime now = now();
        if (!now.isBefore(appointment.paymentExpiresAt())) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        Integer visitNumber = schedule.nextVisitNumber();
        appointments.confirmInventory(schedule.id(), visitNumber);
        payments.markPaid(payment.id());
        appointments.confirmAppointment(appointment.id(), visitNumber);
        return new PaymentResponse(payment.id(), payment.paymentNo(), "PAID",
                appointment.id(), "CONFIRMED", visitNumber);
    }

    @Override
    @Transactional
    public boolean cancelOwned(Long userId, Long appointmentOrderId) {
        AppointmentLifecycleView lookup = appointments.findAppointment(appointmentOrderId);
        if (lookup == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        ScheduleLifecycleView schedule = appointments.lockSchedule(lookup.scheduleId());
        AppointmentLifecycleView appointment = appointments.lockAppointment(appointmentOrderId);
        if (!appointment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        PaymentLifecycleView payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());

        if (isInactive(appointment.status())) {
            if (!(payment.status() == PaymentOrderStatus.CLOSED || payment.status() == PaymentOrderStatus.REFUND_PENDING
                    || payment.status() == PaymentOrderStatus.REFUNDED)) {
                throw integrityFailure("已关闭预约的支付单状态不一致", appointmentOrderId, payment.id());
            }
            return payment.status() == PaymentOrderStatus.REFUND_PENDING;
        }
        LocalDateTime now = now();
        if (appointment.status() == AppointmentStatus.CONFIRMED) {
            if (payment.status() != PaymentOrderStatus.PAID) {
                throw integrityFailure("已确认预约的支付单状态不一致", appointmentOrderId, payment.id());
            }
            LocalDateTime deadline = LocalDateTime.of(appointment.appointmentDate(), appointment.startTime()).minusHours(24);
            if (!now.isBefore(deadline)) throw new BusinessException(ErrorCode.CANCELLATION_DEADLINE_REACHED);
            appointments.releaseConfirmedInventory(schedule.id());
            refunds.requestFullRefund(payment.id(), RefundReason.APPOINTMENT_CANCELLED);
            appointments.cancelConfirmedAppointment(appointmentOrderId, now);
            return true;
        }
        if (appointment.status() == AppointmentStatus.PENDING_PAYMENT && payment.status() == PaymentOrderStatus.PAID) {
            AppointmentCloseReason closedStatus = now.isBefore(appointment.paymentExpiresAt()) ? AppointmentCloseReason.USER_CANCELLED : AppointmentCloseReason.PAYMENT_EXPIRED;
            closePaidPending(schedule, appointment, payment, closedStatus, RefundReason.APPOINTMENT_CANCELLED, now);
            return true;
        }
        requirePendingUnpaid(appointment, payment, payment.id());
        closePending(schedule, appointment, payment,
                now.isBefore(appointment.paymentExpiresAt()) ? AppointmentCloseReason.USER_CANCELLED : AppointmentCloseReason.PAYMENT_EXPIRED, now);
        return false;
    }

    @Override
    @Transactional
    public boolean expireIfDue(Long appointmentOrderId) {
        AppointmentLifecycleView lookup = appointments.findAppointment(appointmentOrderId);
        if (lookup == null) return false;
        ScheduleLifecycleView schedule = appointments.lockSchedule(lookup.scheduleId());
        AppointmentLifecycleView appointment = appointments.lockAppointment(appointmentOrderId);
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT) return false;
        LocalDateTime now = now();
        if (now.isBefore(appointment.paymentExpiresAt())) return false;
        PaymentLifecycleView payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if (payment.status() == PaymentOrderStatus.PAID) {
            return resolvePaidPending(schedule, appointment, payment, now);
        }
        requirePendingUnpaid(appointment, payment, payment.id());
        closePending(schedule, appointment, payment, AppointmentCloseReason.PAYMENT_EXPIRED, now);
        return true;
    }

    @Override
    @Transactional
    public boolean reconcilePaidPending(Long appointmentOrderId) {
        AppointmentLifecycleView lookup = appointments.findAppointment(appointmentOrderId);
        if (lookup == null) return false;
        ScheduleLifecycleView schedule = appointments.lockSchedule(lookup.scheduleId());
        AppointmentLifecycleView appointment = appointments.lockAppointment(appointmentOrderId);
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT) return false;
        PaymentLifecycleView payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if (payment.status() != PaymentOrderStatus.PAID) return false;
        return resolvePaidPending(schedule, appointment, payment, now());
    }

    @Override
    @Transactional
    public boolean ensureRefundForClosedPaid(Long appointmentOrderId) {
        AppointmentLifecycleView lookup = appointments.findAppointment(appointmentOrderId);
        if (lookup == null) return false;
        appointments.lockSchedule(lookup.scheduleId());
        AppointmentLifecycleView appointment = appointments.lockAppointment(appointmentOrderId);
        if (!isInactive(appointment.status())) return false;
        PaymentLifecycleView payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if (payment.status() == PaymentOrderStatus.REFUND_PENDING || payment.status() == PaymentOrderStatus.REFUNDED) return false;
        if (payment.status() != PaymentOrderStatus.PAID) return false;
        refunds.requestFullRefund(payment.id(), RefundReason.RECONCILIATION_CLOSED_PAID);
        return true;
    }

    private void closePending(ScheduleLifecycleView schedule, AppointmentLifecycleView appointment,
                              PaymentLifecycleView payment, AppointmentCloseReason status, LocalDateTime now) {
        appointments.releaseHeldInventory(schedule.id());
        appointments.closePendingAppointment(appointment.id(), status, now);
        payments.closeUnpaid(payment.id());
        log.info("预约关闭，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.id(), payment.id(), status.status().name());
    }

    private void closePaidPending(ScheduleLifecycleView schedule, AppointmentLifecycleView appointment,
                                  PaymentLifecycleView payment, AppointmentCloseReason status, RefundReason reason,
                                  LocalDateTime now) {
        refunds.requestFullRefund(payment.id(), reason);
        appointments.releaseHeldInventory(schedule.id());
        appointments.closePendingAppointment(appointment.id(), status, now);
        log.info("已支付待确认预约关闭并进入退款，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.id(), payment.id(), status.status().name());
    }

    private void requirePendingUnpaid(AppointmentLifecycleView appointment, PaymentLifecycleView payment, Long paymentId) {
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT
                || !(payment.status() == PaymentOrderStatus.UNPAID || payment.status() == PaymentOrderStatus.PAYING)) {
            throw integrityFailure("预约与支付状态组合不一致", appointment.id(), paymentId);
        }
    }

    private boolean resolvePaidPending(ScheduleLifecycleView schedule, AppointmentLifecycleView appointment,
                                       PaymentLifecycleView payment, LocalDateTime now) {
        boolean paidAtOrAfterDeadline = payment.paidAt() == null
                || !payment.paidAt().isBefore(appointment.paymentExpiresAt());
        if (paidAtOrAfterDeadline) {
            refunds.requestFullRefund(payment.id(), RefundReason.APPOINTMENT_NOT_CONFIRMABLE);
            appointments.releaseHeldInventory(schedule.id());
            appointments.closePendingAppointment(appointment.id(), AppointmentCloseReason.PAYMENT_EXPIRED, now);
            return true;
        }
        Integer visitNumber = schedule.nextVisitNumber();
        appointments.confirmInventory(schedule.id(), visitNumber);
        appointments.confirmAppointment(appointment.id(), visitNumber);
        log.info("对账发现已支付事实并恢复确认，appointmentOrderId={}, paymentOrderId={}",
                appointment.id(), payment.id());
        return true;
    }

    private void validateIntegrity(AppointmentLifecycleView appointment, PaymentLifecycleView payment,
                                   Long expectedPaymentId) {
        if (payment == null) throw integrityFailure("预约关联的支付单不存在", appointment.id(), expectedPaymentId);
        if (expectedPaymentId != null && !payment.id().equals(expectedPaymentId)) {
            throw integrityFailure("支付单关联关系不一致", appointment.id(), expectedPaymentId);
        }
        if (!payment.appointmentOrderId().equals(appointment.id()) || !payment.userId().equals(appointment.userId())) {
            throw integrityFailure("预约与支付单归属不一致", appointment.id(), payment.id());
        }
    }

    private boolean isInactive(AppointmentStatus status) {
        return status == AppointmentStatus.CANCELLED || status == AppointmentStatus.EXPIRED || status == AppointmentStatus.CANCELLED_BY_HOSPITAL;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private PaymentResponse response(PaymentLifecycleView payment, AppointmentLifecycleView appointment) {
        return new PaymentResponse(payment.id(), payment.paymentNo(), payment.status().name(),
                appointment.id(), appointment.status().name(), appointment.visitNumber());
    }

    private BusinessException integrityFailure(String message, Long appointmentId, Long paymentId) {
        log.error("{}，appointmentOrderId={}, paymentOrderId={}", message, appointmentId, paymentId);
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

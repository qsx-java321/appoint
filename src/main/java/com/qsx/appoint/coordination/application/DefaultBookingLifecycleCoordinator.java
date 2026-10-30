package com.qsx.appoint.coordination.application;

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

        if ("CONFIRMED".equals(appointment.status()) && "PAID".equals(payment.status())) {
            return response(payment, appointment);
        }
        if ("EXPIRED".equals(appointment.status())) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        if (isInactive(appointment.status())) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        requirePendingUnpaid(appointment, payment, paymentId);

        LocalDateTime now = now();
        if (!now.isBefore(appointment.paymentExpiresAt())) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        if (!"OPEN".equals(schedule.status())) throw new BusinessException(ErrorCode.SCHEDULE_CLOSED);
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
            if (!("CLOSED".equals(payment.status()) || "REFUND_PENDING".equals(payment.status())
                    || "REFUNDED".equals(payment.status()))) {
                throw integrityFailure("已关闭预约的支付单状态不一致", appointmentOrderId, payment.id());
            }
            return "REFUND_PENDING".equals(payment.status());
        }
        LocalDateTime now = now();
        if ("CONFIRMED".equals(appointment.status())) {
            if (!"PAID".equals(payment.status())) {
                throw integrityFailure("已确认预约的支付单状态不一致", appointmentOrderId, payment.id());
            }
            LocalDateTime deadline = LocalDateTime.of(appointment.appointmentDate(), appointment.startTime()).minusHours(24);
            if (!now.isBefore(deadline)) throw new BusinessException(ErrorCode.CANCELLATION_DEADLINE_REACHED);
            appointments.releaseConfirmedInventory(schedule.id());
            refunds.requestFullRefund(payment.id(), "APPOINTMENT_CANCELLED");
            appointments.cancelConfirmedAppointment(appointmentOrderId, now);
            return true;
        }
        if ("PENDING_PAYMENT".equals(appointment.status()) && "PAID".equals(payment.status())) {
            String closedStatus = now.isBefore(appointment.paymentExpiresAt()) ? "CANCELLED" : "EXPIRED";
            closePaidPending(schedule, appointment, payment, closedStatus, "APPOINTMENT_CANCELLED", now);
            return true;
        }
        requirePendingUnpaid(appointment, payment, payment.id());
        closePending(schedule, appointment, payment,
                now.isBefore(appointment.paymentExpiresAt()) ? "CANCELLED" : "EXPIRED", now);
        return false;
    }

    @Override
    @Transactional
    public boolean expireIfDue(Long appointmentOrderId) {
        AppointmentLifecycleView lookup = appointments.findAppointment(appointmentOrderId);
        if (lookup == null) return false;
        ScheduleLifecycleView schedule = appointments.lockSchedule(lookup.scheduleId());
        AppointmentLifecycleView appointment = appointments.lockAppointment(appointmentOrderId);
        if (!"PENDING_PAYMENT".equals(appointment.status())) return false;
        LocalDateTime now = now();
        if (now.isBefore(appointment.paymentExpiresAt())) return false;
        PaymentLifecycleView payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if ("PAID".equals(payment.status())) {
            return resolvePaidPending(schedule, appointment, payment, now);
        }
        requirePendingUnpaid(appointment, payment, payment.id());
        closePending(schedule, appointment, payment, "EXPIRED", now);
        return true;
    }

    @Override
    @Transactional
    public boolean reconcilePaidPending(Long appointmentOrderId) {
        AppointmentLifecycleView lookup = appointments.findAppointment(appointmentOrderId);
        if (lookup == null) return false;
        ScheduleLifecycleView schedule = appointments.lockSchedule(lookup.scheduleId());
        AppointmentLifecycleView appointment = appointments.lockAppointment(appointmentOrderId);
        if (!"PENDING_PAYMENT".equals(appointment.status())) return false;
        PaymentLifecycleView payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if (!"PAID".equals(payment.status())) return false;
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
        if ("REFUND_PENDING".equals(payment.status()) || "REFUNDED".equals(payment.status())) return false;
        if (!"PAID".equals(payment.status())) return false;
        refunds.requestFullRefund(payment.id(), "RECONCILIATION_CLOSED_PAID");
        return true;
    }

    @Override
    @Transactional
    public void closeScheduleLocked(Long scheduleId, Long operatorUserId, LocalDateTime now) {
        appointments.closeSchedule(scheduleId, operatorUserId, now);
        ScheduleLifecycleView schedule = new ScheduleLifecycleView(scheduleId, "CLOSED", null);
        for (AppointmentLifecycleView appointment : appointments.lockPendingAppointments(scheduleId)) {
            PaymentLifecycleView payment = payments.lockByAppointment(appointment.id());
            validateIntegrity(appointment, payment, payment == null ? null : payment.id());
            if ("PAID".equals(payment.status())) {
                String closedStatus = now.isBefore(appointment.paymentExpiresAt())
                        ? "CANCELLED_BY_HOSPITAL" : "EXPIRED";
                closePaidPending(schedule, appointment, payment, closedStatus,
                        "SCHEDULE_CLOSED_BEFORE_CONFIRMATION", now);
                continue;
            }
            requirePendingUnpaid(appointment, payment, payment.id());
            closePending(schedule, appointment, payment,
                    now.isBefore(appointment.paymentExpiresAt()) ? "CANCELLED_BY_HOSPITAL" : "EXPIRED", now);
        }
        log.info("排班关闭，scheduleId={}, operatorUserId={}", scheduleId, operatorUserId);
    }

    private void closePending(ScheduleLifecycleView schedule, AppointmentLifecycleView appointment,
                              PaymentLifecycleView payment, String status, LocalDateTime now) {
        appointments.releaseHeldInventory(schedule.id());
        appointments.closePendingAppointment(appointment.id(), status, now);
        payments.closeUnpaid(payment.id());
        log.info("预约关闭，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.id(), payment.id(), status);
    }

    private void closePaidPending(ScheduleLifecycleView schedule, AppointmentLifecycleView appointment,
                                  PaymentLifecycleView payment, String status, String reason,
                                  LocalDateTime now) {
        refunds.requestFullRefund(payment.id(), reason);
        appointments.releaseHeldInventory(schedule.id());
        appointments.closePendingAppointment(appointment.id(), status, now);
        log.info("已支付待确认预约关闭并进入退款，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.id(), payment.id(), status);
    }

    private void requirePendingUnpaid(AppointmentLifecycleView appointment, PaymentLifecycleView payment, Long paymentId) {
        if (!"PENDING_PAYMENT".equals(appointment.status())
                || !("UNPAID".equals(payment.status()) || "PAYING".equals(payment.status()))) {
            throw integrityFailure("预约与支付状态组合不一致", appointment.id(), paymentId);
        }
    }

    private boolean resolvePaidPending(ScheduleLifecycleView schedule, AppointmentLifecycleView appointment,
                                       PaymentLifecycleView payment, LocalDateTime now) {
        boolean paidAfterDeadline = payment.paidAt() == null
                || payment.paidAt().isAfter(appointment.paymentExpiresAt());
        if (!"OPEN".equals(schedule.status()) || paidAfterDeadline) {
            refunds.requestFullRefund(payment.id(), "APPOINTMENT_NOT_CONFIRMABLE");
            appointments.releaseHeldInventory(schedule.id());
            appointments.closePendingAppointment(appointment.id(), "EXPIRED", now);
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

    private boolean isInactive(String status) {
        return "CANCELLED".equals(status) || "EXPIRED".equals(status) || "CANCELLED_BY_HOSPITAL".equals(status);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private PaymentResponse response(PaymentLifecycleView payment, AppointmentLifecycleView appointment) {
        return new PaymentResponse(payment.id(), payment.paymentNo(), payment.status(),
                appointment.id(), appointment.status(), appointment.visitNumber());
    }

    private BusinessException integrityFailure(String message, Long appointmentId, Long paymentId) {
        log.error("{}，appointmentOrderId={}, paymentOrderId={}", message, appointmentId, paymentId);
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

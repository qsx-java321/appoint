package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.catalog.model.BookingTimeRules;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.PaymentLifecycle;
import com.qsx.appoint.payment.application.PaymentLifecycleCase;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
class DefaultBookingLifecycleCoordinator implements BookingLifecycleCoordinator {
    private final AppointmentLifecycle appointments;
    private final PaymentLifecycle payments;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public void payOwned(Long userId, Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = lockOwned(userId, appointmentOrderId);
        PaymentLifecycleCase payment = lockPayment(appointment);
        if (appointment.status() == AppointmentStatus.CONFIRMED && payment.status() == PaymentOrderStatus.PAID) {
            return;
        }
        if (appointment.status() == AppointmentStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
        requirePendingUnpaid(appointment, payment);
        LocalDateTime paidAt = now();
        if (BookingTimeRules.isPaymentExpired(appointment.paymentExpiresAt(), paidAt)) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
        payments.markPaid(payment, paidAt);
        appointments.confirm(appointment);
    }

    @Override
    @Transactional
    public void cancelOwned(Long userId, Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = lockOwned(userId, appointmentOrderId);
        PaymentLifecycleCase payment = lockPayment(appointment);
        if (appointment.status() == AppointmentStatus.CANCELLED || appointment.status() == AppointmentStatus.EXPIRED
                || appointment.status() == AppointmentStatus.CANCELLED_BY_HOSPITAL) {
            return;
        }
        requirePendingUnpaid(appointment, payment);
        LocalDateTime closedAt = now();
        AppointmentCloseReason reason = BookingTimeRules.isPaymentExpired(appointment.paymentExpiresAt(), closedAt)
                ? AppointmentCloseReason.PAYMENT_EXPIRED : AppointmentCloseReason.USER_CANCELLED;
        closePending(appointment, payment, reason, closedAt);
    }

    @Override
    @Transactional
    public boolean expireIfDue(Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null || appointment.status() != AppointmentStatus.PENDING_PAYMENT) return false;
        LocalDateTime closedAt = now();
        if (!BookingTimeRules.isPaymentExpired(appointment.paymentExpiresAt(), closedAt)) return false;

        PaymentLifecycleCase payment = lockPayment(appointment);
        // 升级前已收款的历史记录保留资金事实，按既有时间继续确认按时付款的预约。
        if (payment.status() == PaymentOrderStatus.PAID && payment.paidAt() != null
                && payment.paidAt().isBefore(appointment.paymentExpiresAt())) {
            appointments.confirm(appointment);
            return true;
        }
        if (!payment.status().isUnpaid()) return false;

        closePending(appointment, payment, AppointmentCloseReason.PAYMENT_EXPIRED, closedAt);
        return true;
    }

    private AppointmentLifecycleCase lockOwned(Long userId, Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!appointment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return appointment;
    }

    private PaymentLifecycleCase lockPayment(AppointmentLifecycleCase appointment) {
        PaymentLifecycleCase payment = payments.lockByAppointment(appointment.id());
        if (payment == null || !appointment.id().equals(payment.appointmentOrderId())
                || !appointment.userId().equals(payment.userId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return payment;
    }

    private void requirePendingUnpaid(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment) {
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT || !payment.status().isUnpaid()
                || appointment.paymentExpiresAt() == null) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
    }

    private void closePending(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                              AppointmentCloseReason reason, LocalDateTime closedAt) {
        appointments.closePending(appointment, reason, closedAt);
        payments.closeUnpaid(payment);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

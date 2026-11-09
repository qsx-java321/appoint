package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.payment.application.PaymentLifecycleCase;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import java.time.LocalDateTime;

/** 只判断跨模块业务事实；锁定、完整性、取时及副作用由用例负责。 */
final class BookingLifecyclePolicy {
    enum ExpirationDecision { NO_ACTION, CONFIRM, CLOSE_UNPAID, REFUND_LATE_PAYMENT, INVALID_STATE }
    enum CancellationAction { NO_ACTION, CLOSE_UNPAID, CLOSE_PAID_AND_REFUND, CANCEL_CONFIRMED_AND_REFUND, REJECT_DEADLINE, INVALID_STATE }
    record CancellationDecision(CancellationAction action, AppointmentCloseReason closeReason,
                                RefundReason refundReason, boolean refundPending) { }
    enum PaymentConfirmationDecision { CONFIRM, ALREADY_CONFIRMED, REFUND_CLOSED, EXPIRE_AND_REFUND }

    ExpirationDecision decideExpiration(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                                         LocalDateTime now) {
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT || now.isBefore(appointment.paymentExpiresAt())) {
            return ExpirationDecision.NO_ACTION;
        }
        if (payment.status() == PaymentOrderStatus.PAID) return decidePaidPending(appointment,payment);
        return unpaid(payment.status()) ? ExpirationDecision.CLOSE_UNPAID : ExpirationDecision.INVALID_STATE;
    }

    ExpirationDecision decidePaidPending(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment) {
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT || payment.status() != PaymentOrderStatus.PAID) {
            return ExpirationDecision.NO_ACTION;
        }
        return payment.paidAt() != null && payment.paidAt().isBefore(appointment.paymentExpiresAt())
                ? ExpirationDecision.CONFIRM : ExpirationDecision.REFUND_LATE_PAYMENT;
    }

    CancellationDecision decideCancellation(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                                             LocalDateTime now) {
        if (inactive(appointment.status())) {
            boolean pending = payment.status() == PaymentOrderStatus.REFUND_PENDING;
            boolean closed = pending || payment.status() == PaymentOrderStatus.REFUNDED || payment.status() == PaymentOrderStatus.CLOSED;
            return new CancellationDecision(closed ? CancellationAction.NO_ACTION : CancellationAction.INVALID_STATE,null,null,pending);
        }
        if (appointment.status() == AppointmentStatus.CONFIRMED) {
            if (payment.status() != PaymentOrderStatus.PAID) return invalidCancellation();
            LocalDateTime deadline = LocalDateTime.of(appointment.appointmentDate(),appointment.startTime()).minusHours(24);
            if (!now.isBefore(deadline)) return new CancellationDecision(CancellationAction.REJECT_DEADLINE,null,null,false);
            return new CancellationDecision(CancellationAction.CANCEL_CONFIRMED_AND_REFUND,null,RefundReason.APPOINTMENT_CANCELLED,true);
        }
        AppointmentCloseReason reason = now.isBefore(appointment.paymentExpiresAt())
                ? AppointmentCloseReason.USER_CANCELLED : AppointmentCloseReason.PAYMENT_EXPIRED;
        if (payment.status() == PaymentOrderStatus.PAID) {
            return new CancellationDecision(CancellationAction.CLOSE_PAID_AND_REFUND,reason,RefundReason.APPOINTMENT_CANCELLED,true);
        }
        return unpaid(payment.status()) ? new CancellationDecision(CancellationAction.CLOSE_UNPAID,reason,null,false) : invalidCancellation();
    }

    PaymentConfirmationDecision decidePaymentConfirmation(AppointmentLifecycleCase appointment, PaymentSucceededEvent event) {
        if (appointment.status() == AppointmentStatus.CONFIRMED) return PaymentConfirmationDecision.ALREADY_CONFIRMED;
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT) return PaymentConfirmationDecision.REFUND_CLOSED;
        return event.paidAt().isBefore(appointment.paymentExpiresAt())
                ? PaymentConfirmationDecision.CONFIRM : PaymentConfirmationDecision.EXPIRE_AND_REFUND;
    }

    boolean requiresClosedPaidRefund(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment) {
        return inactive(appointment.status()) && payment.status() == PaymentOrderStatus.PAID;
    }
    boolean inactive(AppointmentStatus status) {
        return status == AppointmentStatus.CANCELLED || status == AppointmentStatus.EXPIRED || status == AppointmentStatus.CANCELLED_BY_HOSPITAL;
    }
    private boolean unpaid(PaymentOrderStatus status) { return status == PaymentOrderStatus.UNPAID || status == PaymentOrderStatus.PAYING; }
    private CancellationDecision invalidCancellation() { return new CancellationDecision(CancellationAction.INVALID_STATE,null,null,false); }
}

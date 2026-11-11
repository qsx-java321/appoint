package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.payment.application.PaymentLifecycleCase;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class BookingLifecyclePolicyTest {
    private final BookingLifecyclePolicy policy = new BookingLifecyclePolicy();
    private static final LocalDateTime NOW = LocalDateTime.of(2030,1,1,8,0);

    @ParameterizedTest
    @CsvSource({
        "PENDING_PAYMENT,UNPAID,1,CLOSE_UNPAID,USER_CANCELLED,,false",
        "PENDING_PAYMENT,PAYING,1,CLOSE_UNPAID,USER_CANCELLED,,false",
        "PENDING_PAYMENT,PAID,1,CLOSE_PAID_AND_REFUND,USER_CANCELLED,APPOINTMENT_CANCELLED,true",
        "PENDING_PAYMENT,UNPAID,0,CLOSE_UNPAID,PAYMENT_EXPIRED,,false",
        "PENDING_PAYMENT,PAYING,-1,CLOSE_UNPAID,PAYMENT_EXPIRED,,false",
        "PENDING_PAYMENT,PAID,0,CLOSE_PAID_AND_REFUND,PAYMENT_EXPIRED,APPOINTMENT_CANCELLED,true",
        "CANCELLED,CLOSED,1,NO_ACTION,,,false", "EXPIRED,CLOSED,1,NO_ACTION,,,false",
        "CANCELLED_BY_HOSPITAL,CLOSED,1,NO_ACTION,,,false",
        "CANCELLED,REFUND_PENDING,1,NO_ACTION,,,true", "EXPIRED,REFUND_PENDING,1,NO_ACTION,,,true",
        "CANCELLED_BY_HOSPITAL,REFUND_PENDING,1,NO_ACTION,,,true",
        "CANCELLED,REFUNDED,1,NO_ACTION,,,false", "EXPIRED,REFUNDED,1,NO_ACTION,,,false",
        "CANCELLED_BY_HOSPITAL,REFUNDED,1,NO_ACTION,,,false",
        "CONFIRMED,PAID,1,CANCEL_CONFIRMED_AND_REFUND,,APPOINTMENT_CANCELLED,true",
        "CONFIRMED,UNPAID,1,INVALID_STATE,,,false", "CANCELLED,PAID,1,INVALID_STATE,,,false",
        "PENDING_PAYMENT,CLOSED,1,INVALID_STATE,,,false"
    })
    void cancellationPreservesCloseReasonRefundReasonAndBoolean(AppointmentStatus appointment, PaymentOrderStatus payment,
            int expiryOffset, BookingLifecyclePolicy.CancellationAction action, AppointmentCloseReason closeReason,
            RefundReason refundReason, boolean refundPending) {
        var decision = policy.decideCancellation(appointment(appointment,NOW.plusSeconds(expiryOffset),NOW.plusDays(2)),
                payment(payment,NOW),NOW);
        assertThat(decision.action()).isEqualTo(action);
        assertThat(decision.closeReason()).isEqualTo(closeReason);
        assertThat(decision.refundReason()).isEqualTo(refundReason);
        assertThat(decision.refundPending()).isEqualTo(refundPending);
    }

    @ParameterizedTest
    @CsvSource({"-1,CANCEL_CONFIRMED_AND_REFUND", "0,REJECT_DEADLINE", "1,REJECT_DEADLINE"})
    void confirmedCancellationUsesStrictTwentyFourHourBoundary(int secondsAfterDeadline,
            BookingLifecyclePolicy.CancellationAction expected) {
        var appointment = appointment(AppointmentStatus.CONFIRMED,NOW.plusMinutes(15),NOW.plusDays(1));
        var decision = policy.decideCancellation(appointment,payment(PaymentOrderStatus.PAID,NOW),NOW.plusSeconds(secondsAfterDeadline));
        assertThat(decision.action()).isEqualTo(expected);
        if (expected == BookingLifecyclePolicy.CancellationAction.REJECT_DEADLINE) {
            assertThat(decision.refundReason()).isNull();
            assertThat(decision.refundPending()).isFalse();
        }
    }

    @ParameterizedTest
    @CsvSource({"UNPAID,,CLOSE_UNPAID", "PAYING,,CLOSE_UNPAID", "PAID,-1,CONFIRM",
            "PAID,0,REFUND_LATE_PAYMENT", "PAID,1,REFUND_LATE_PAYMENT", "PAID,,REFUND_LATE_PAYMENT",
            "CLOSED,,INVALID_STATE", "REFUND_PENDING,,INVALID_STATE", "REFUNDED,,INVALID_STATE"})
    void expirationUsesPersistedPaidAtIncludingMissing(PaymentOrderStatus payment,Integer paidOffset,
            BookingLifecyclePolicy.ExpirationDecision expected) {
        assertThat(policy.decideExpiration(appointment(AppointmentStatus.PENDING_PAYMENT,NOW,NOW.plusDays(2)),
                payment(payment,paidOffset == null ? null : NOW.plusSeconds(paidOffset)),NOW)).isEqualTo(expected);
    }

    @Test
    void shortCircuitsAndReconciliationKeepDistinctTimeEligibility() {
        var pending = appointment(AppointmentStatus.PENDING_PAYMENT,NOW.plusMinutes(15),NOW.plusDays(2));
        assertThat(policy.decideExpiration(pending,null,NOW)).isEqualTo(BookingLifecyclePolicy.ExpirationDecision.NO_ACTION);
        assertThat(policy.decideExpiration(appointment(AppointmentStatus.CONFIRMED,NOW,NOW.plusDays(2)),null,NOW))
                .isEqualTo(BookingLifecyclePolicy.ExpirationDecision.NO_ACTION);
        assertThat(policy.decidePaidPending(pending,payment(PaymentOrderStatus.PAID,NOW)))
                .isEqualTo(BookingLifecyclePolicy.ExpirationDecision.CONFIRM);
        assertThat(policy.decidePaidPending(pending,payment(PaymentOrderStatus.UNPAID,null)))
                .isEqualTo(BookingLifecyclePolicy.ExpirationDecision.NO_ACTION);
    }

    @ParameterizedTest
    @CsvSource({"PENDING_PAYMENT,-1,CONFIRM", "PENDING_PAYMENT,0,EXPIRE_AND_REFUND", "PENDING_PAYMENT,1,EXPIRE_AND_REFUND",
            "CONFIRMED,-1,ALREADY_CONFIRMED", "CONFIRMED,0,ALREADY_CONFIRMED", "CONFIRMED,1,ALREADY_CONFIRMED",
            "CANCELLED,-1,REFUND_CLOSED", "EXPIRED,1,REFUND_CLOSED", "CANCELLED_BY_HOSPITAL,0,REFUND_CLOSED"})
    void eventConfirmationUsesEventPaidAtAndDoesNotRestoreClosedAppointments(AppointmentStatus status,int offset,
            BookingLifecyclePolicy.PaymentConfirmationDecision expected) {
        var event = new PaymentSucceededEvent("00000000-0000-0000-0000-000000000001",1,NOW,4L,1L,5L,
                new BigDecimal("10.00"),"CNY","MOCK",NOW.plusSeconds(offset));
        assertThat(policy.decidePaymentConfirmation(appointment(status,NOW,NOW.plusDays(2)),event)).isEqualTo(expected);
    }

    @Test
    void closedPaidReconciliationOnlyEnsuresMissingRefund() {
        for (var appointment : AppointmentStatus.values()) {
            for (var payment : PaymentOrderStatus.values()) {
                boolean expected = (appointment == AppointmentStatus.CANCELLED || appointment == AppointmentStatus.EXPIRED
                        || appointment == AppointmentStatus.CANCELLED_BY_HOSPITAL) && payment == PaymentOrderStatus.PAID;
                assertThat(policy.requiresClosedPaidRefund(appointment(appointment,NOW,NOW.plusDays(2)),payment(payment,NOW)))
                        .as("%s/%s",appointment,payment).isEqualTo(expected);
            }
        }
    }

    private AppointmentLifecycleCase appointment(AppointmentStatus status,LocalDateTime expiry,LocalDateTime opening) {
        return new AppointmentLifecycleCase(1L,2L,3L,status,expiry,opening.toLocalDate(),opening.toLocalTime(),null,1L);
    }
    private PaymentLifecycleCase payment(PaymentOrderStatus status,LocalDateTime paidAt) {
        return new PaymentLifecycleCase(4L,"P4",1L,2L,status,paidAt);
    }
}

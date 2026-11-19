package com.qsx.appoint.mysql;

import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.support.MySqlBusinessTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentConsistencyMySqlIT extends MySqlBusinessTestSupport {
    @Autowired BookingLifecycleCoordinator lifecycle;
    @MockitoSpyBean AppointmentLifecycle appointments;

    @Test
    void concurrentPaymentAndRetryKeepTheOriginalReceipt() throws Exception {
        var actor = actor("110105199001010010");
        var booking = create(actor);
        Long appointmentId = booking.getAppointmentOrderId();
        Callable<Integer> pay = () -> payments.pay(actor.user(), appointmentId).getVisitNumber();

        assertThat(race(List.of(pay, pay))).containsExactly(1, 1);
        var paidAt = payments.pay(actor.user(), appointmentId).getPaidAt();
        assertThat(paidAt).isNotNull();
        clock.set("2026-11-05T12:16:00");
        var replay = payments.pay(actor.user(), appointmentId);
        assertThat(replay.getPaidAt()).isEqualTo(paidAt);
        assertThat(replay.getVisitNumber()).isEqualTo(1);
        inventory(20, 19, 0, 1);
        assertThat(count("payment_outbox_events")).isZero();
        assertThat(count("coordination_inbox_events")).isZero();
        assertThat(count("payment_attempts")).isZero();
        assertThat(count("payment_refunds")).isZero();
    }

    @Test
    void paymentAndCancellationConvergeToOneOutcome() throws Exception {
        var actor = actor("110105199001010010");
        var booking = create(actor);
        Long id = booking.getAppointmentOrderId();

        assertThat(race(List.of(
                () -> runAction(() -> lifecycle.payOwned(actor.user(), id)),
                () -> runAction(() -> lifecycle.cancelOwned(actor.user(), id)))))
                .containsExactlyInAnyOrder(ErrorCode.ORDER_STATE_ERROR.name(), "OK");

        if (state("appointment_orders", id).equals("CONFIRMED")) {
            assertThat(state("payment_orders", booking.getPaymentOrderId())).isEqualTo("PAID");
            inventory(20, 19, 0, 1);
        } else {
            assertThat(state("appointment_orders", id)).isEqualTo("CANCELLED");
            assertThat(state("payment_orders", booking.getPaymentOrderId())).isEqualTo("CLOSED");
            inventory(20, 20, 0, 0);
        }
    }

    @Test
    void failedConfirmationRollsBackPaymentAndCanRetry() {
        var actor = actor("110105199001010010");
        var booking = create(actor);
        Long id = booking.getAppointmentOrderId();
        AppointmentLifecycle target = AopTestUtils.getUltimateTargetObject(appointments);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected-after-confirm");
        }).when(target).confirm(any());
        try {
            assertThatThrownBy(() -> lifecycle.payOwned(actor.user(), id))
                    .isInstanceOf(IllegalStateException.class).hasMessage("injected-after-confirm");
            assertThat(state("appointment_orders", id)).isEqualTo("PENDING_PAYMENT");
            assertThat(state("payment_orders", booking.getPaymentOrderId())).isEqualTo("UNPAID");
            assertThat(jdbc.queryForObject("SELECT next_visit_number FROM schedules WHERE doctor_id=?",
                    Integer.class, doctor)).isEqualTo(1);
            inventory(20, 19, 1, 0);
        } finally {
            reset(target);
        }
        assertThat(payments.pay(actor.user(), id).getVisitNumber()).isEqualTo(1);
        inventory(20, 19, 0, 1);
    }

    private String runAction(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }

    private String state(String table, Long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=?", String.class, id);
    }
}

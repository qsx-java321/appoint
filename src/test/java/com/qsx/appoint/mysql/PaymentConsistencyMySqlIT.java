package com.qsx.appoint.mysql;

import com.qsx.appoint.support.MySqlBusinessTestSupport;
import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.coordination.application.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 收款先提交，再控制成功事件与取消的顺序，验证Inbox、库存和退款一致性。 */
class PaymentConsistencyMySqlIT extends MySqlBusinessTestSupport {
    @Autowired PaymentSucceededProcessor processor;
    @Autowired BookingLifecycleCoordinator lifecycle;
    @MockitoSpyBean AppointmentLifecycle appointments;
    private String state(String table,long id) { return jdbc.queryForObject("SELECT status FROM "+table+" WHERE id=?",String.class,id); }
    @Test void duplicateEventConfirmsOnlyOnce() {
        var a=actor("110105199001010010"); var order=create(a);
        var event=payments.recordPaymentSuccess(a.user(),order.getPaymentOrderId());
        assertThat(processor.process(event)).isEqualTo(PaymentSucceededResult.CONFIRMED);
        var number=jdbc.queryForObject("SELECT visit_number FROM appointment_orders WHERE id=?",Integer.class,order.getAppointmentOrderId());
        assertThat(processor.process(event)).isEqualTo(PaymentSucceededResult.DUPLICATE);
        assertThat(count("coordination_inbox_events")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT visit_number FROM appointment_orders WHERE id=?",Integer.class,order.getAppointmentOrderId())).isEqualTo(number);
        assertThat(jdbc.queryForObject("SELECT next_visit_number FROM schedules WHERE doctor_id=?",Integer.class,doctor)).isEqualTo(number+1);
        assertThat(state("appointment_orders",order.getAppointmentOrderId())).isEqualTo("CONFIRMED"); inventory(20,19,0,1);
    }
    @ParameterizedTest
    @ValueSource(strings={"cancel-first","confirm-first","concurrent"})
    void cancellationAndConfirmationConvergeToOneRefund(String orderOfExecution) throws Exception {
        var a=actor("110105199001010010"); var order=create(a);
        var event=payments.recordPaymentSuccess(a.user(),order.getPaymentOrderId());
        if(orderOfExecution.equals("cancel-first")) {
            lifecycle.cancelOwned(a.user(),order.getAppointmentOrderId()); processor.process(event);
        } else if(orderOfExecution.equals("confirm-first")) {
            processor.process(event); lifecycle.cancelOwned(a.user(),order.getAppointmentOrderId());
        } else {
            race(List.of(() -> {processor.process(event); return true;}, () -> lifecycle.cancelOwned(a.user(),order.getAppointmentOrderId())));
        }
        assertThat(state("appointment_orders",order.getAppointmentOrderId())).isEqualTo("CANCELLED");
        assertThat(state("payment_orders",order.getPaymentOrderId())).isEqualTo("REFUND_PENDING");
        assertThat(count("payment_refunds")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE event_type='RefundRequested'",Long.class)).isEqualTo(1);
        assertThat(count("appointment_active_claims")).isZero(); inventory(20,20,0,0);
    }
    @Test void failedConfirmationRollsBackInboxAndCanRetry() {
        var a=actor("110105199001010010"); var order=create(a);
        var event=payments.recordPaymentSuccess(a.user(),order.getPaymentOrderId());
        AppointmentLifecycle target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(appointments);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("injected-after-confirm"); })
                .when(target).confirm(any());
        try {
            assertThatThrownBy(() -> processor.process(event)).isInstanceOf(IllegalStateException.class).hasMessage("injected-after-confirm");
            assertThat(count("coordination_inbox_events")).isZero();
            assertThat(state("appointment_orders",order.getAppointmentOrderId())).isEqualTo("PENDING_PAYMENT");
            assertThat(state("payment_orders",order.getPaymentOrderId())).isEqualTo("PAID");
            assertThat(count("payment_outbox_events")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT next_visit_number FROM schedules WHERE doctor_id=?",Integer.class,doctor)).isEqualTo(1);
            inventory(20,19,1,0);
        } finally { reset(target); }
        assertThat(processor.process(event)).isEqualTo(PaymentSucceededResult.CONFIRMED);
        assertThat(count("coordination_inbox_events")).isEqualTo(1); inventory(20,19,0,1);
    }
}

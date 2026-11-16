package com.qsx.appoint.mysql;

import com.qsx.appoint.support.MySqlBusinessTestSupport;
import com.qsx.appoint.payment.application.PaymentOrderCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 在真实支付INSERT之后注入故障，验证跨模块同库事务的原子性。 */
class BookingRollbackMySqlIT extends MySqlBusinessTestSupport {
    @MockitoSpyBean PaymentOrderCreationService paymentCreation;
    @Test void failureAfterPaymentInsertRollsBackWholeCreationAndKeyCanRetry() {
        var a=actor("110105199001010010"); var key=UUID.randomUUID().toString();
        PaymentOrderCreationService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(paymentCreation);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            assertThat(count("payment_orders")).isEqualTo(1);
            throw new IllegalStateException("injected-after-payment-insert");
        }).when(target).createOrder(any());
        try {
            assertThatThrownBy(() -> bookings.create(a.user(),key,request(a)))
                    .isInstanceOf(IllegalStateException.class).hasMessage("injected-after-payment-insert");
            for(String table:List.of("appointment_orders","payment_orders","appointment_active_claims","appointment_create_requests"))
                assertThat(count(table)).as(table).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules WHERE doctor_id=?",Long.class,doctor)).isZero();
        } finally { reset(target); }
        bookings.create(a.user(),key,request(a));
        assertThat(count("payment_orders")).isEqualTo(1); inventory(20,19,1,0);
    }
}

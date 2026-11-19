package com.qsx.appoint.mysql;

import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.support.MySqlBusinessTestSupport;
import com.qsx.appoint.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MySqlIntegrationTest(properties = {
        "appoint.expiration.enabled=true",
        "appoint.expiration.scan-delay=100ms",
        "appoint.expiration.batch-size=1"
})
class AppointmentExpirationMySqlIT extends MySqlBusinessTestSupport {
    @Autowired BookingLifecycleCoordinator lifecycle;

    @Test
    void scheduledScanClosesUnpaidOrdersAcrossBatchesAndKeepsPaidOrder() {
        var payingUser = actor("110105199001010010");
        var paid = create(payingUser);
        var firstUnpaid = create(actor("110105199002020026"));
        var secondUnpaid = create(actor("110105199003030031"));

        clock.set("2026-11-05T12:15:00");
        assertThat(lifecycle.expireIfDue(firstUnpaid.getAppointmentOrderId())).isFalse();
        var receipt = payments.pay(payingUser.user(), paid.getAppointmentOrderId());
        assertThat(receipt.getVisitNumber()).isEqualTo(1);
        inventory(20, 17, 2, 1);

        clock.set("2026-11-05T12:15:01");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(appointmentStatus(firstUnpaid.getAppointmentOrderId())).isEqualTo("EXPIRED");
            assertThat(appointmentStatus(secondUnpaid.getAppointmentOrderId())).isEqualTo("EXPIRED");
        });

        assertThat(paymentStatus(firstUnpaid.getPaymentOrderId())).isEqualTo("CLOSED");
        assertThat(paymentStatus(secondUnpaid.getPaymentOrderId())).isEqualTo("CLOSED");
        assertThat(lifecycle.expireIfDue(firstUnpaid.getAppointmentOrderId())).isFalse();
        assertThat(appointmentStatus(paid.getAppointmentOrderId())).isEqualTo("CONFIRMED");
        assertThat(paymentStatus(paid.getPaymentOrderId())).isEqualTo("PAID");
        assertThat(payments.pay(payingUser.user(), paid.getAppointmentOrderId()).getPaidAt())
                .isEqualTo(receipt.getPaidAt());
        assertThat(jdbc.queryForList("SELECT appointment_order_id FROM appointment_active_claims", Long.class))
                .containsExactly(paid.getAppointmentOrderId());
        inventory(20, 19, 0, 1);
    }

    private String appointmentStatus(Long id) {
        return jdbc.queryForObject("SELECT status FROM appointment_orders WHERE id=?", String.class, id);
    }

    private String paymentStatus(Long id) {
        return jdbc.queryForObject("SELECT status FROM payment_orders WHERE id=?", String.class, id);
    }
}

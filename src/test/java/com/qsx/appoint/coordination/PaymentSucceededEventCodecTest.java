package com.qsx.appoint.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.coordination.messaging.PaymentSucceededEventCodec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSucceededEventCodecTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PaymentSucceededEventCodec codec = new PaymentSucceededEventCodec(objectMapper);

    @Test
    void readsVersionedEnvelopeAndRejectsUnknownContract() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String json = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "eventType", "PaymentSucceeded",
                "eventVersion", 1,
                "aggregateId", "51",
                "occurredAt", "2026-10-30T12:00:00",
                "payload", Map.of(
                        "paymentOrderId", "51",
                        "appointmentOrderId", "41",
                        "paymentAttemptId", "61",
                        "amount", new BigDecimal("50.00"),
                        "currency", "CNY",
                        "channel", "MOCK",
                        "paidAt", "2026-10-30T11:59:59")));

        var event = codec.read(json.getBytes());

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.paymentOrderId()).isEqualTo(51L);
        assertThat(event.appointmentOrderId()).isEqualTo(41L);
        assertThat(event.amount()).isEqualByComparingTo("50.00");
        assertThat(event.paidAt()).isEqualTo(LocalDateTime.of(2026, 10, 30, 11, 59, 59));

        assertThatThrownBy(() -> codec.read(json.replace("PaymentSucceeded", "PaymentSettled").getBytes()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> codec.read(json.replace("\"eventVersion\":1", "\"eventVersion\":2").getBytes()))
                .isInstanceOf(BusinessException.class);
    }
}

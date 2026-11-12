package com.qsx.appoint.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qsx.appoint.payment.application.PaymentOutbox;
import com.qsx.appoint.payment.contract.event.*;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

/** 从真实接口写数据库，再与改造前 fixture 比较；不把新 DTO 自比较当契约证明。 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentOutboxTransactionTest {
    @Autowired PaymentOutbox outbox;
    @Autowired PaymentOutboxEventMapper events;
    @Autowired ObjectMapper mapper;
    @Autowired TransactionTemplate transactions;

    @ParameterizedTest
    @ValueSource(strings={"PaymentSucceeded","RefundRequested","RefundSucceeded","RefundFailed"})
    void storedEventPreservesHistoricalContractAndRollsBackWithCaller(String type) throws Exception {
        ObjectNode expected;
        try (var input = getClass().getResourceAsStream("/messaging/v1/"+type+".json")) {
            expected = (ObjectNode) mapper.readTree(input);
        }
        ObjectNode fixture = expected;
        String id = transactions.execute(status -> append(type, fixture));
        var stored = events.selectById(id);
        var actual = mapper.readTree(stored.getPayload());
        if (actual.isTextual()) actual = mapper.readTree(actual.textValue()); // H2 JSON 驱动适配仅在测试侧。
        expected.put("eventId",id);
        assertThat(actual).isEqualTo(expected);
        assertThat(stored.getStatus()).isEqualTo("PENDING");
        assertThat(stored.getPublishAttempts()).isZero();
        String rolledBack = transactions.execute(status -> {
            String eventId = append(type, fixture);
            status.setRollbackOnly();
            return eventId;
        });
        assertThat(events.selectById(rolledBack)).isNull();
    }

    @Test
    void recordingRequiresTheBusinessTransaction() {
        assertThatThrownBy(() -> outbox.recordRefundFailed(new RefundFailedPayload(71L,"REF71",51L,null,"failed"),
                LocalDateTime.now())).isInstanceOf(IllegalTransactionStateException.class);
    }

    private String append(String type, ObjectNode fixture) {
        var time = LocalDateTime.parse(fixture.path("occurredAt").asText());
        var p = fixture.path("payload");
        return switch(type) {
            case "PaymentSucceeded" -> outbox.recordPaymentSucceeded(mapper.convertValue(p,PaymentSucceededPayload.class),time);
            case "RefundRequested" -> outbox.recordRefundRequested(mapper.convertValue(p,RefundRequestedPayload.class),time);
            case "RefundSucceeded" -> outbox.recordRefundSucceeded(mapper.convertValue(p,RefundSucceededPayload.class),time);
            default -> outbox.recordRefundFailed(mapper.convertValue(p,RefundFailedPayload.class),time);
        };
    }
}

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
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-outbox-transaction-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class PaymentOutboxTransactionTest {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate fixtureJdbc;
    private com.qsx.appoint.support.CommittedFixtureScope fixtureScope;

    @org.junit.jupiter.api.BeforeEach
    void trackCommittedFixture() { fixtureScope = new com.qsx.appoint.support.CommittedFixtureScope(fixtureJdbc); }

    @org.junit.jupiter.api.AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Autowired PaymentOutbox outbox;
    @Autowired PaymentOutboxEventMapper events;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ObjectMapper mapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void productionMapperKeepsTextLongAndExplicitNull() throws Exception {
        // 使用Spring实际配置的mapper，不能用测试自建mapper证明生产Long/null契约。
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("id", 51L);
        values.put("failureMessage", null);
        var serialized = mapper.readTree(mapper.writeValueAsBytes(values));
        assertThat(serialized.get("id").isTextual()).isTrue();
        assertThat(serialized.get("id").textValue()).isEqualTo("51");
        assertThat(serialized.has("failureMessage")).isTrue();
        assertThat(serialized.get("failureMessage").isNull()).isTrue();
    }

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
    void serializationFailureRollsBackTheCallersBusinessWrite() throws Exception {
        String email = "outbox-rollback-" + java.util.UUID.randomUUID() + "@example.com";
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM payment_outbox_events",Integer.class);
        org.mockito.Mockito.doThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialize failed") {})
                .when(mapper).writeValueAsString(org.mockito.ArgumentMatchers.any(PaymentEventEnvelope.class));
        try {
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO users(email,password_hash) VALUES (?,?)",email,"test");
                outbox.recordRefundFailed(new RefundFailedPayload(71L,"REF71",51L,null,"failed"),LocalDateTime.now());
            })).isInstanceOf(com.qsx.appoint.common.BusinessException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,email)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_outbox_events",Integer.class)).isEqualTo(before);
        } finally { org.mockito.Mockito.reset(mapper); }
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

package com.qsx.appoint.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.payment.application.OutboxPublisher;
import com.qsx.appoint.payment.application.DeadLetterReplayService;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {
        "appoint.expiration.enabled=false",
        "appoint.messaging.enabled=true",
        "appoint.outbox.enabled=false",
        "appoint.outbox.initial-backoff=10ms",
        "appoint.outbox.confirm-timeout=2s"
})
class PaymentConfirmationReliabilityIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appoint").withUsername("appoint").withPassword("appoint-test");
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> "true");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PaymentOutboxEventMapper outboxMapper;
    @Autowired private OutboxPublisher publisher;
    @Autowired private CachingConnectionFactory connectionFactory;
    @Autowired private DeadLetterReplayService replayService;

    @Test
    void brokerOutageKeepsEventThenRecoveryConfirmsExactlyOnce() throws Exception {
        fixture();
        PaymentOutboxEvent event = pendingEvent();
        outboxMapper.insert(event);
        RABBIT.execInContainer("rabbitmqctl", "stop_app");

        assertThat(publisher.publishDue()).isZero();
        assertThat(outboxMapper.selectById(event.getEventId()).getStatus()).isEqualTo("PENDING");

        RABBIT.execInContainer("rabbitmqctl", "start_app");
        RABBIT.execInContainer("rabbitmqctl", "await_startup");
        connectionFactory.resetConnection();
        await().untilAsserted(() -> {
            jdbcTemplate.update("UPDATE payment_outbox_events SET next_attempt_at=? WHERE event_id=?",
                    LocalDateTime.now().minusSeconds(1), event.getEventId());
            assertThat(publisher.publishDue()).isEqualTo(1);
        });

        await().untilAsserted(() -> assertThat(appointmentStatus()).isEqualTo("CONFIRMED"));
        rabbitTemplate.convertAndSend(RabbitEventTopology.DOMAIN_EXCHANGE,
                RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY, event.getPayload(), message -> {
                    message.getMessageProperties().setMessageId(event.getEventId());
                    return message;
                });
        await().untilAsserted(() -> {
            assertThat(inboxCount(event.getEventId())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT confirmed_capacity FROM schedules WHERE id=4", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT next_visit_number FROM schedules WHERE id=4", Integer.class)).isEqualTo(2);
        });

        rabbitTemplate.convertAndSend(RabbitEventTopology.DEAD_LETTER_EXCHANGE,
                RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, event.getPayload(), message -> {
                    message.getMessageProperties().setMessageId(event.getEventId());
                    return message;
                });
        await().untilAsserted(() -> assertThat(replayService.replayOne(
                RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq")).isEqualTo(event.getEventId()));
        await().untilAsserted(() -> assertThat(inboxCount(event.getEventId())).isEqualTo(1));
    }

    @Test
    void malformedPermanentMessageIsRejectedToDlq() {
        rabbitTemplate.convertAndSend(RabbitEventTopology.DOMAIN_EXCHANGE,
                RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY,
                "{\"eventId\":\"broken\",\"eventType\":\"PaymentSucceeded\",\"eventVersion\":2}");

        await().untilAsserted(() -> {
            Message message = rabbitTemplate.receive(
                    RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq", 500);
            assertThat(message).isNotNull();
            assertThat(new String(message.getBody())).contains("broken");
        });
    }

    @Test
    void refundGatewayFailureRetriesThenMovesToDlqWithoutRestoringAppointment() throws Exception {
        refundFixture();
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", 22_006L);
        payload.put("refundNo", "REF22006");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", "RefundRequested");
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", 22_004L);
        envelope.put("occurredAt", LocalDateTime.now());
        envelope.put("payload", payload);

        rabbitTemplate.convertAndSend(RabbitEventTopology.DOMAIN_EXCHANGE,
                RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY,
                objectMapper.writeValueAsString(envelope), message -> {
                    message.getMessageProperties().setMessageId(eventId);
                    return message;
                });

        await().untilAsserted(() -> {
            Message deadLetter = rabbitTemplate.receive(
                    RabbitEventTopology.REFUND_WORKER_QUEUE + ".dlq", 500);
            assertThat(deadLetter).isNotNull();
            assertThat(new String(deadLetter.getBody())).contains("REF22006");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM payment_refunds WHERE id=22006", String.class)).isEqualTo("FAILED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT attempt_count FROM payment_refunds WHERE id=22006", Integer.class)).isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM payment_orders WHERE id=22004", String.class)).isEqualTo("REFUND_PENDING");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM appointment_orders WHERE id=22003", String.class)).isEqualTo("CANCELLED");
        });
    }

    private PaymentOutboxEvent pendingEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        LocalDateTime paidAt = LocalDateTime.of(2098, 12, 1, 8, 0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentOrderId", 21_004L);
        payload.put("appointmentOrderId", 21_003L);
        payload.put("paymentAttemptId", 21_005L);
        payload.put("amount", new BigDecimal("50.00"));
        payload.put("currency", "CNY");
        payload.put("channel", "MOCK");
        payload.put("paidAt", paidAt);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", "PaymentSucceeded");
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", 21_004L);
        envelope.put("occurredAt", paidAt);
        envelope.put("payload", payload);

        PaymentOutboxEvent event = new PaymentOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("PaymentOrder");
        event.setAggregateId(21_004L);
        event.setEventType("PaymentSucceeded");
        event.setEventVersion(1);
        event.setPayload(objectMapper.writeValueAsString(envelope));
        event.setOccurredAt(paidAt);
        event.setStatus("PENDING");
        event.setPublishAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        return event;
    }

    private void fixture() {
        jdbcTemplate.update("""
                UPDATE schedules SET status='OPEN', session_type='MORNING', start_time='08:00:00', end_time='12:00:00',
                  total_capacity=2, available_capacity=1, held_capacity=1, confirmed_capacity=0,
                  next_visit_number=1, registration_fee=50.00, fee_currency='CNY' WHERE id=4
                """);
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (21001,'reliable-confirm@example.com','hash')");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (21002,21001,'测试','RESIDENT_ID_CARD','110105199001012101','13800000000','p@example.com')
                """);
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency)
                VALUES (21003,'A21003',21001,4,21002,'测试','RESIDENT_ID_CARD','110105199001012101',
                        '13800000000','p@example.com',1,'DOC000001','张医生','主任医师',1,'内科',1,
                        '示例市第一人民医院','示例市健康路 1 号','2099-01-01','09:00:00','09:30:00',
                        'PENDING_PAYMENT','2098-12-01 08:15:00',50.00,'CNY')
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (21004,'P21004',21003,21001,'PAID',50.00,'CNY','2098-12-01 08:15:00',FALSE)
                """);
    }

    private void refundFixture() {
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (22001,'refund-dlq@example.com','hash')");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (22002,22001,'退款测试','RESIDENT_ID_CARD','110105199001012202','13800000000','p@example.com')
                """);
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency,closed_at)
                VALUES (22003,'A22003',22001,4,22002,'退款测试','RESIDENT_ID_CARD','110105199001012202',
                        '13800000000','p@example.com',1,'DOC000001','张医生','主任医师',1,'内科',1,
                        '示例市第一人民医院','示例市健康路 1 号','2099-01-01','09:00:00','09:30:00',
                        'CANCELLED','2098-12-01 08:15:00',50.00,'CNY',CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,paid_at,legacy)
                VALUES (22004,'P22004',22003,22001,'REFUND_PENDING',50.00,'CNY',
                        '2098-12-01 08:15:00','2098-12-01 08:00:00',FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_attempts
                  (id,attempt_no,payment_order_id,channel,provider_order_no,provider_transaction_no,
                   status,created_at,processing_at,completed_at,updated_at)
                VALUES (22005,'ATT22005',22004,'WECHAT_PAY','provider-order-22005','provider-txn-22005',
                        'SUCCEEDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_refunds
                  (id,refund_no,payment_order_id,payment_attempt_id,amount,currency,channel,status,reason,
                   attempt_count,created_at,updated_at)
                VALUES (22006,'REF22006',22004,22005,50.00,'CNY','WECHAT_PAY','PENDING',
                        'APPOINTMENT_CANCELLED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
    }

    private String appointmentStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM appointment_orders WHERE id=21003", String.class);
    }

    private int inboxCount(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coordination_inbox_events WHERE event_id=?", Integer.class, eventId);
    }
}

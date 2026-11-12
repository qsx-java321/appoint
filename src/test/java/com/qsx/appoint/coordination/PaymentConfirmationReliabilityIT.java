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
@org.springframework.test.context.ActiveProfiles("test")
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
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.3.2-management");

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
    @Autowired private org.springframework.context.ApplicationContext applicationContext;

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
    void failedConfirmationIsRetriedTwiceThenDeadLettered() throws Exception {
        PaymentOutboxEvent event = pendingEvent();
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(event.getPayload());
        ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.get("payload"))
                .put("appointmentOrderId", 99_999_999L);
        rabbitTemplate.convertAndSend(RabbitEventTopology.DOMAIN_EXCHANGE,
                RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY,
                objectMapper.writeValueAsString(envelope));

        await().untilAsserted(() -> {
            Message deadLetter = rabbitTemplate.receive(
                    RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq", 500);
            assertThat(deadLetter).isNotNull();
            assertThat(new String(deadLetter.getBody())).contains(event.getEventId());
            assertThat(deadLetter.getMessageProperties().<Integer>getHeader("payment-succeeded-attempt"))
                    .isEqualTo(2);
            assertThat(inboxCount(event.getEventId())).isZero();
        });
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
        payload.put("paymentOrderId", 22_004L);
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

    @Test
    void committedConfirmationSurvivesLostAckAndBrokerRedelivery() throws Exception {
        ackFixture();
        PaymentOutboxEvent event = pendingEvent();
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(event.getPayload());
        envelope.put("aggregateId", 23004L);
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) envelope.get("payload");
        payload.put("paymentOrderId", 23004L);
        payload.put("appointmentOrderId", 23003L);
        payload.put("paymentAttemptId", 23005L);
        byte[] body = objectMapper.writeValueAsBytes(envelope);
        var processor = applicationContext.getBean(com.qsx.appoint.coordination.application.PaymentSucceededProcessor.class);
        var codec = applicationContext.getBean(com.qsx.appoint.coordination.messaging.PaymentSucceededEventCodec.class);
        var listener = new com.qsx.appoint.coordination.messaging.PaymentSucceededListener(
                applicationContext.getBean(com.qsx.appoint.common.messaging.RabbitConsumerFailureHandler.class), codec, processor);
        String queue = "ack-window-" + UUID.randomUUID();
        var connection = connectionFactory.createConnection();
        var channel = connection.createChannel(false);
        try {
            channel.queueDeclare(queue, true, false, false, null);
            channel.basicPublish("", queue, null, body);
            var first = channel.basicGet(queue, false);
            assertThat(first).isNotNull();
            var failingAck = org.mockito.Mockito.mock(com.rabbitmq.client.Channel.class,
                    org.mockito.AdditionalAnswers.delegatesTo(channel));
            org.mockito.Mockito.doThrow(new java.io.IOException("ACK lost after commit"))
                    .when(failingAck).basicAck(first.getEnvelope().getDeliveryTag(), false);
            var converter = new org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.consume(new Message(first.getBody(),
                    converter.toMessageProperties(first.getProps(), first.getEnvelope(), "UTF-8")), failingAck))
                    .isInstanceOf(java.io.IOException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=23003", String.class)).isEqualTo("CONFIRMED");
            ((org.springframework.amqp.rabbit.connection.ChannelProxy) channel).getTargetChannel().close();
            var redeliveryChannel = connection.createChannel(false);
            try {
                var received = new java.util.concurrent.atomic.AtomicReference<com.rabbitmq.client.GetResponse>();
                await().until(() -> {
                    received.set(redeliveryChannel.basicGet(queue, false));
                    return received.get() != null;
                });
                var duplicate = received.get();
                assertThat(duplicate.getEnvelope().isRedeliver()).isTrue();
                listener.consume(new Message(duplicate.getBody(), converter.toMessageProperties(
                        duplicate.getProps(), duplicate.getEnvelope(), "UTF-8")), redeliveryChannel);
                assertThat(inboxCount(event.getEventId())).isEqualTo(1);
                assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4", Integer.class)).isEqualTo(2);
                assertThat(jdbcTemplate.queryForObject("SELECT confirmed_capacity FROM schedules WHERE id=4", Integer.class)).isEqualTo(1);
                redeliveryChannel.queueDelete(queue);
            } finally {
                redeliveryChannel.close();
            }
        } finally {
            if (channel.isOpen()) channel.close();
            connection.close();
        }
    }

    @Test
    void realSignedCallbackCommitsWhileBrokerIsDownThenConfirmsAfterRecovery() throws Exception {
        callbackFixture();
        var attempts=applicationContext.getBean(com.qsx.appoint.payment.application.PaymentAttemptService.class);
        var gateway=applicationContext.getBean(com.qsx.appoint.payment.adapter.mock.MockPaymentGateway.class);
        var ingress=applicationContext.getBean(com.qsx.appoint.payment.application.PaymentNotificationIngress.class);
        var attempt=attempts.startAttempt(24001L,24004L,com.qsx.appoint.payment.domain.PaymentChannel.MOCK);
        var raw=gateway.complete(attempt.providerOrderNo(),UUID.randomUUID().toString(),LocalDateTime.now());
        String eventId;
        RABBIT.execInContainer("rabbitmqctl","stop_app");
        try {
            var response=ingress.handle(com.qsx.appoint.payment.domain.PaymentChannel.MOCK,raw);
            assertThat(response.processingResult()).isEqualTo("SUCCEEDED");
            eventId=response.eventId();
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment_orders WHERE id=24004",String.class)).isEqualTo("PAID");
            assertThat(outboxMapper.selectById(eventId).getStatus()).isEqualTo("PENDING");
            assertThat(inboxCount(eventId)).isZero();
        } finally {
            RABBIT.execInContainer("rabbitmqctl","start_app");
            RABBIT.execInContainer("rabbitmqctl","await_startup");
            connectionFactory.resetConnection();
        }
        await().untilAsserted(() -> {
            publisher.publishDue();
            assertThat(outboxMapper.selectById(eventId).getStatus()).isEqualTo("PUBLISHED");
        });
        await().untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=24003",String.class)).isEqualTo("CONFIRMED");
            assertThat(inboxCount(eventId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4",Integer.class)).isEqualTo(2);
        });
    }

    @Test
    void failureAfterBusinessWritesBeforeCommitRollsBackThenRetryConfirmsOnce() throws Exception {
        rollbackFixture();
        PaymentOutboxEvent event=pendingEvent();
        var envelope=(com.fasterxml.jackson.databind.node.ObjectNode)objectMapper.readTree(event.getPayload());
        envelope.put("aggregateId",25004L);
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)envelope.get("payload");
        payload.put("paymentOrderId",25004L);
        payload.put("appointmentOrderId",25003L);
        payload.put("paymentAttemptId",25005L);
        event.setAggregateId(25004L);
        event.setPayload(objectMapper.writeValueAsString(envelope));
        var fault=applicationContext.getBean(BeforeCommitFailureProcessor.class).failOnce(event.getEventId());
        outboxMapper.insert(event);
        try {
            publisher.publishDue();
            assertThat(fault.rolledBack.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(inboxCount(event.getEventId())).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=25003",String.class)).isEqualTo("PENDING_PAYMENT");
            assertThat(jdbcTemplate.queryForObject("SELECT held_capacity FROM schedules WHERE id=4",Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT confirmed_capacity FROM schedules WHERE id=4",Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4",Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointment_active_claims WHERE appointment_order_id=25003",Integer.class)).isEqualTo(1);
        } finally { fault.release.countDown(); }
        await().untilAsserted(() -> {
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=25003",String.class)).isEqualTo("CONFIRMED");
            assertThat(inboxCount(event.getEventId())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT confirmed_capacity FROM schedules WHERE id=4",Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT held_capacity FROM schedules WHERE id=4",Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4",Integer.class)).isEqualTo(2);
            assertThat(fault.calls.get()).isEqualTo(2);
        });
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class FailureConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        BeforeCommitFailureProcessor beforeCommitFailureProcessor(
                @org.springframework.beans.factory.annotation.Qualifier("defaultPaymentSucceededProcessor")
                com.qsx.appoint.coordination.application.PaymentSucceededProcessor delegate) {
            return new BeforeCommitFailureProcessor(delegate);
        }
    }

    static class FailureWindow {
        final String eventId;
        final java.util.concurrent.atomic.AtomicBoolean armed=new java.util.concurrent.atomic.AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.CountDownLatch rolledBack=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        FailureWindow(String eventId) { this.eventId=eventId; }
    }

    static class BeforeCommitFailureProcessor implements com.qsx.appoint.coordination.application.PaymentSucceededProcessor {
        private final com.qsx.appoint.coordination.application.PaymentSucceededProcessor delegate;
        private volatile FailureWindow window;
        BeforeCommitFailureProcessor(com.qsx.appoint.coordination.application.PaymentSucceededProcessor delegate) { this.delegate=delegate; }
        public FailureWindow failOnce(String eventId) { window=new FailureWindow(eventId); return window; }
        @Override
        @org.springframework.transaction.annotation.Transactional
        public com.qsx.appoint.coordination.application.PaymentSucceededResult process(com.qsx.appoint.coordination.application.PaymentSucceededEvent event) {
            FailureWindow current=window;
            if (current != null && current.eventId.equals(event.eventId())) current.calls.incrementAndGet();
            var result=delegate.process(event);
            if (current != null && current.eventId.equals(event.eventId()) && current.armed.compareAndSet(true,false)) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override public void afterCompletion(int status) {
                                if (status == STATUS_ROLLED_BACK) {
                                    current.rolledBack.countDown();
                                    try { current.release.await(10,java.util.concurrent.TimeUnit.SECONDS); }
                                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                                }
                            }
                        });
                throw new IllegalStateException("测试注入：业务写入后、提交前失败");
            }
            return result;
        }
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

    private void ackFixture() {
        jdbcTemplate.update("""
                UPDATE schedules SET status='OPEN', session_type='MORNING', start_time='08:00:00', end_time='12:00:00',
                  total_capacity=2, available_capacity=1, held_capacity=1, confirmed_capacity=0,
                  next_visit_number=1, registration_fee=50.00, fee_currency='CNY' WHERE id=4
                """);
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (23001,'ack-window@example.com','hash')");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (23002,23001,'测试','RESIDENT_ID_CARD','110105199001012101','13800000000','p@example.com')
                """);
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency)
                VALUES (23003,'A23003',23001,4,23002,'测试','RESIDENT_ID_CARD','110105199001012101',
                        '13800000000','p@example.com',1,'DOC000001','张医生','主任医师',1,'内科',1,
                        '示例市第一人民医院','示例市健康路 1 号','2099-01-01','09:00:00','09:30:00',
                        'PENDING_PAYMENT','2098-12-01 08:15:00',50.00,'CNY')
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (23004,'P23004',23003,23001,'PAID',50.00,'CNY','2098-12-01 08:15:00',FALSE)
                """);
    }

    private void rollbackFixture() {
        jdbcTemplate.update("""
                UPDATE schedules SET status='OPEN', session_type='MORNING', start_time='08:00:00', end_time='12:00:00',
                  total_capacity=2, available_capacity=1, held_capacity=1, confirmed_capacity=0,
                  next_visit_number=1, registration_fee=50.00, fee_currency='CNY' WHERE id=4
                """);
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (25001,'before-commit@example.com','hash')");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (25002,25001,'测试','RESIDENT_ID_CARD','110105199001012501','13800000000','p@example.com')
                """);
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency)
                VALUES (25003,'A25003',25001,4,25002,'测试','RESIDENT_ID_CARD','110105199001012501',
                        '13800000000','p@example.com',1,'DOC000001','张医生','主任医师',1,'内科',1,
                        '示例市第一人民医院','示例市健康路 1 号','2099-01-01','09:00:00','09:30:00',
                        'PENDING_PAYMENT','2098-12-01 08:15:00',50.00,'CNY')
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (25004,'P25004',25003,25001,'PAID',50.00,'CNY','2098-12-01 08:15:00',FALSE)
                """);
        jdbcTemplate.update("INSERT INTO appointment_active_claims (schedule_id,appointment_order_id,patient_id,document_type,document_no) VALUES (4,25003,25002,'RESIDENT_ID_CARD','110105199001012501')");
    }

    private void callbackFixture() {
        jdbcTemplate.update("""
                UPDATE schedules SET status='OPEN', session_type='MORNING', start_time='08:00:00', end_time='12:00:00',
                  total_capacity=2, available_capacity=1, held_capacity=1, confirmed_capacity=0,
                  next_visit_number=1, registration_fee=50.00, fee_currency='CNY' WHERE id=4
                """);
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (24001,'callback-outage@example.com','hash')");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (24002,24001,'测试','RESIDENT_ID_CARD','110105199001012101','13800000000','p@example.com')
                """);
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency)
                VALUES (24003,'A24003',24001,4,24002,'测试','RESIDENT_ID_CARD','110105199001012101',
                        '13800000000','p@example.com',1,'DOC000001','张医生','主任医师',1,'内科',1,
                        '示例市第一人民医院','示例市健康路 1 号','2099-01-01','09:00:00','09:30:00',
                        'PENDING_PAYMENT','2098-12-01 08:15:00',50.00,'CNY')
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (24004,'P24004',24003,24001,'UNPAID',50.00,'CNY','2098-12-01 08:15:00',FALSE)
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

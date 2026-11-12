package com.qsx.appoint.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.payment.application.OutboxPublisher;
import com.qsx.appoint.payment.application.OutboxMessagePublisher;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "appoint.expiration.enabled=false",
        "appoint.outbox.enabled=false",
        "appoint.outbox.initial-backoff=10ms",
        "appoint.outbox.max-attempts=3",
        "appoint.outbox.confirm-timeout=2s"
})
class OutboxPublisherReliabilityIT {
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

    @Autowired private OutboxPublisher publisher;
    @Autowired private OutboxMessagePublisher messagePublisher;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private PaymentOutboxEventMapper outboxMapper;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void confirmedPersistentMessageMarksOutboxPublishedInDurableTopology() throws Exception {
        PaymentOutboxEvent event = pendingEvent();
        outboxMapper.insert(event);

        assertThat(publisher.publishDue()).isEqualTo(1);

        PaymentOutboxEvent published = outboxMapper.selectById(event.getEventId());
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPublishedAt()).isNotNull();
        Message message = rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 5_000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getEventId());
        assertThat(objectMapper.readTree(message.getBody())).isEqualTo(objectMapper.readTree(event.getPayload()));
    }

    @Test
    void mandatoryReturnRetriesThenExhaustedEventBecomesFailed() {
        Binding binding = BindingBuilder.bind(new Queue(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE))
                .to(new TopicExchange(RabbitEventTopology.DOMAIN_EXCHANGE))
                .with(RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY);
        rabbitAdmin.removeBinding(binding);
        PaymentOutboxEvent returned = pendingEvent();
        outboxMapper.insert(returned);

        assertThat(publisher.publishDue()).isZero();
        PaymentOutboxEvent retrying = outboxMapper.selectById(returned.getEventId());
        assertThat(retrying.getStatus()).isEqualTo("PENDING");
        assertThat(retrying.getPublishAttempts()).isEqualTo(1);
        assertThat(retrying.getNextAttemptAt()).isAfter(returned.getNextAttemptAt());
        assertThat(retrying.getLastError()).contains("mandatory return");
        rabbitAdmin.declareBinding(binding);
        forceDue(returned.getEventId());
        assertThat(publisher.publishDue()).isEqualTo(1);
        assertThat(rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 5_000)).isNotNull();

        PaymentOutboxEvent poison = pendingEvent();
        poison.setEventType("UnknownEvent");
        outboxMapper.insert(poison);
        for (int attempt = 1; attempt <= 3; attempt++) {
            forceDue(poison.getEventId());
            assertThat(publisher.publishDue()).isZero();
            PaymentOutboxEvent current = outboxMapper.selectById(poison.getEventId());
            assertThat(current.getPublishAttempts()).isEqualTo(attempt);
            assertThat(current.getStatus()).isEqualTo(attempt == 3 ? "FAILED" : "PENDING");
        }
    }

    @Test
    void brokerOutageKeepsPendingEventAndRecoveryPublishesIt() throws Exception {
        PaymentOutboxEvent event = pendingEvent();
        outboxMapper.insert(event);
        RABBIT.execInContainer("rabbitmqctl", "stop_app");

        assertThat(publisher.publishDue()).isZero();
        PaymentOutboxEvent pending = outboxMapper.selectById(event.getEventId());
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getPublishAttempts()).isEqualTo(1);

        RABBIT.execInContainer("rabbitmqctl", "start_app");
        RABBIT.execInContainer("rabbitmqctl", "await_startup");
        forceDue(event.getEventId());
        assertThat(publisher.publishDue()).isEqualTo(1);
        assertThat(outboxMapper.selectById(event.getEventId()).getStatus()).isEqualTo("PUBLISHED");
        assertThat(rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 5_000)).isNotNull();
    }

    @Test
    void deadLetterTopologyRoutesRejectedBusinessMessageToItsDlq() throws Exception {
        rabbitTemplate.convertAndSend(RabbitEventTopology.DOMAIN_EXCHANGE,
                RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY, "{\"eventId\":\"dlq-test\"}");
        rabbitTemplate.execute(channel -> {
            var delivery = channel.basicGet(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, false);
            assertThat(delivery).isNotNull();
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
            return null;
        });

        Message deadLetter = rabbitTemplate.receive(
                RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq", 5_000);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody())).contains("dlq-test");
    }

    @Test
    void concurrentPublishersUseSkipLockedAndPublishOneCopy() throws Exception {
        PaymentOutboxEvent event = pendingEvent();
        outboxMapper.insert(event);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(publisher::publishDue);
            Future<Integer> second = executor.submit(publisher::publishDue);
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(0, 1);
        }
        assertThat(outboxMapper.selectById(event.getEventId()).getStatus()).isEqualTo("PUBLISHED");
        assertThat(rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 5_000)).isNotNull();
        assertThat(rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 200)).isNull();
    }

    @Test
    void crashAfterConfirmBeforeDatabaseMarkProducesRecoverableDuplicate() throws Exception {
        PaymentOutboxEvent event = pendingEvent();
        outboxMapper.insert(event);
        PaymentOutboxEvent stored = outboxMapper.selectById(event.getEventId());

        messagePublisher.publish(new com.qsx.appoint.payment.application.OutboxMessage(stored.getEventId(), stored.getEventType(), stored.getEventVersion(), stored.getPayload()));
        messagePublisher.publish(new com.qsx.appoint.payment.application.OutboxMessage(stored.getEventId(), stored.getEventType(), stored.getEventVersion(), stored.getPayload()));

        assertThat(outboxMapper.selectById(event.getEventId()).getStatus()).isEqualTo("PENDING");
        Message first = rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 5_000);
        Message second = rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 5_000);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getMessageProperties().getMessageId()).isEqualTo(event.getEventId());
        assertThat(second.getMessageProperties().getMessageId()).isEqualTo(event.getEventId());
    }

    @Test
    void retryTransportDelaysBothRetriesAndStopsAfterThreeAttempts() throws Exception {
        String eventId = UUID.randomUUID().toString();
        var event = new com.qsx.appoint.coordination.application.PaymentSucceededEvent(eventId, 1,
                LocalDateTime.now(), 1L, 2L, 3L, new java.math.BigDecimal("10.00"), "CNY", "MOCK", LocalDateTime.now());
        var codec = org.mockito.Mockito.mock(com.qsx.appoint.coordination.messaging.PaymentSucceededEventCodec.class);
        org.mockito.Mockito.when(codec.read(org.mockito.ArgumentMatchers.any())).thenReturn(event);
        var processor = org.mockito.Mockito.mock(com.qsx.appoint.coordination.application.PaymentSucceededProcessor.class);
        org.mockito.Mockito.when(processor.process(event)).thenThrow(new IllegalStateException("temporary failure"));
        var listener = new com.qsx.appoint.coordination.messaging.PaymentSucceededListener(
                new com.qsx.appoint.common.messaging.RabbitConsumerFailureHandler(new com.qsx.appoint.common.messaging.ConfirmedMessagePublisher(rabbitTemplate)), codec, processor);
        rabbitTemplate.convertAndSend(RabbitEventTopology.DOMAIN_EXCHANGE,
                RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY, eventId);
        var times = new java.util.ArrayList<Long>();
        for (int i = 0; i < 3; i++) {
            final int failures = i;
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() ->
                    Boolean.TRUE.equals(rabbitTemplate.execute(channel -> {
                        var delivery = channel.basicGet(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, false);
                        if (delivery == null) return false;
                        var properties = new org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter()
                                .toMessageProperties(delivery.getProps(), delivery.getEnvelope(), "UTF-8");
                        assertThat(properties.<Integer>getHeader("payment-succeeded-attempt"))
                                .isEqualTo(failures == 0 ? null : failures);
                        times.add(System.nanoTime());
                        listener.consume(new Message(delivery.getBody(), properties), channel);
                        return true;
                    })));
        }
        assertThat(times.get(1) - times.get(0)).isGreaterThanOrEqualTo(900_000_000L);
        assertThat(times.get(2) - times.get(1)).isGreaterThanOrEqualTo(900_000_000L);
        org.mockito.Mockito.verify(processor, org.mockito.Mockito.times(3)).process(event);
        Message dead = rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq", 5_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), java.nio.charset.StandardCharsets.UTF_8)).contains(eventId);
        assertThat(rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 200)).isNull();
    }

    @Test
    void replayRequiresRoutingConfirmationAndResetsCountersWithoutChangingEventId() {
        String eventId = UUID.randomUUID().toString();
        String dlq = RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq";
        Binding binding = BindingBuilder.bind(new Queue(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE))
                .to(new TopicExchange(RabbitEventTopology.DOMAIN_EXCHANGE))
                .with(RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY);
        var replay = new com.qsx.appoint.payment.messaging.RabbitDeadLetterReplayService(rabbitTemplate, objectMapper,
                new com.qsx.appoint.common.messaging.ConfirmedMessagePublisher(rabbitTemplate));
        rabbitTemplate.convertAndSend(RabbitEventTopology.DEAD_LETTER_EXCHANGE,
                RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE,
                "{\"eventId\":\"" + eventId + "\",\"eventType\":\"PaymentSucceeded\"}", message -> {
                    message.getMessageProperties().setHeader("payment-succeeded-attempt", 2);
                    message.getMessageProperties().setHeader("refund-attempt", 2);
                    message.getMessageProperties().setMessageId(eventId);
                    return message;
                });
        org.awaitility.Awaitility.await().untilAsserted(() ->
                assertThat(rabbitAdmin.getQueueProperties(dlq).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(1));
        rabbitAdmin.removeBinding(binding);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> replay.replayOne(com.qsx.appoint.payment.application.ReplayTarget.PAYMENT_SUCCEEDED))
                    .isInstanceOf(RuntimeException.class);
            assertThat(rabbitAdmin.getQueueProperties(dlq).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).isEqualTo(1);
        } finally {
            rabbitAdmin.declareBinding(binding);
        }
        assertThat(replay.replayOne(com.qsx.appoint.payment.application.ReplayTarget.PAYMENT_SUCCEEDED).eventId()).isEqualTo(eventId);
        Message restored = rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 5_000);
        assertThat(restored).isNotNull();
        assertThat(restored.getMessageProperties().getMessageId()).isEqualTo(eventId);
        assertThat(restored.getMessageProperties().getHeaders()).doesNotContainKeys("payment-succeeded-attempt", "refund-attempt");
        assertThat(restored.getMessageProperties().getExpiration()).isNull();
        assertThat(new String(restored.getBody(), java.nio.charset.StandardCharsets.UTF_8)).contains(eventId);
        assertThat(replay.replayOne(com.qsx.appoint.payment.application.ReplayTarget.PAYMENT_SUCCEEDED).status()).isEqualTo(com.qsx.appoint.payment.application.ReplayResult.Status.EMPTY);
    }

    @Test
    void databaseMarkFailureRollsBackAfterBrokerAcceptedThenReusesOriginalEvent() {
        // 本类共享隔离容器；清除前面传输用例留下的待发夹具，避免批量发布读到无关消息。
        jdbcTemplate.update("DELETE FROM payment_outbox_events");
        rabbitAdmin.purgeQueue(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE);
        PaymentOutboxEvent event = pendingEvent();
        outboxMapper.insert(event);
        org.mockito.Mockito.doThrow(new IllegalStateException("mark failed"))
                .when(outboxMapper).markPublished(org.mockito.ArgumentMatchers.eq(event.getEventId()), org.mockito.ArgumentMatchers.any());
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> publisher.publishDue())
                    .isInstanceOf(IllegalStateException.class);
            assertThat(outboxMapper.selectById(event.getEventId()).getStatus()).isEqualTo("PENDING");
            assertThat(outboxMapper.selectById(event.getEventId()).getPublishAttempts()).isZero();
            Message first = rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, 5000);
            assertThat(first).isNotNull();
            assertThat(first.getMessageProperties().getMessageId()).isEqualTo(event.getEventId());
        } finally { org.mockito.Mockito.reset(outboxMapper); }
        assertThat(publisher.publishDue()).isEqualTo(1);
        Message retry = rabbitTemplate.receive(RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE,5000);
        assertThat(retry).isNotNull();
        assertThat(retry.getMessageProperties().getMessageId()).isEqualTo(event.getEventId());
        assertThat(retry.getBody()).isEqualTo(outboxMapper.selectById(event.getEventId()).getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void forceDue(String eventId) {
        jdbcTemplate.update("UPDATE payment_outbox_events SET next_attempt_at = ? WHERE event_id = ?",
                LocalDateTime.now().minusSeconds(1), eventId);
    }

    private PaymentOutboxEvent pendingEvent() {
        PaymentOutboxEvent event = new PaymentOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType("PaymentOrder");
        event.setAggregateId(91L);
        event.setEventType("PaymentSucceeded");
        event.setEventVersion(1);
        event.setPayload("{\"eventId\":\"" + event.getEventId() + "\",\"eventType\":\"PaymentSucceeded\"}");
        event.setOccurredAt(LocalDateTime.now());
        event.setStatus("PENDING");
        event.setPublishAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        return event;
    }
}

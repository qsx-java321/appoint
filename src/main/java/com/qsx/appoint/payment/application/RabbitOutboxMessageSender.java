package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.config.OutboxProperties;
import com.qsx.appoint.common.messaging.ConfirmedMessagePublisher;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
class RabbitOutboxMessageSender implements OutboxMessagePublisher {
    private static final Map<String, String> ROUTING_KEYS = Map.of(
            "PaymentSucceeded", RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY,
            "RefundRequested", RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY,
            "RefundSucceeded", RabbitEventTopology.REFUND_SUCCEEDED_ROUTING_KEY,
            "RefundFailed", RabbitEventTopology.REFUND_FAILED_ROUTING_KEY);

    private final ConfirmedMessagePublisher publisher;
    private final OutboxProperties properties;

    @Override
    public void publish(PaymentOutboxEvent event) throws Exception {
        String routingKey = ROUTING_KEYS.get(event.getEventType());
        if (routingKey == null) throw new IllegalArgumentException("不支持的 Outbox 事件类型: " + event.getEventType());
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setMessageId(event.getEventId());
        messageProperties.setContentType("application/json");
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setHeader("eventType", event.getEventType());
        messageProperties.setHeader("eventVersion", event.getEventVersion());
        Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), messageProperties);
        publisher.publish(RabbitEventTopology.DOMAIN_EXCHANGE, routingKey, message, properties.confirmTimeout());
    }
}

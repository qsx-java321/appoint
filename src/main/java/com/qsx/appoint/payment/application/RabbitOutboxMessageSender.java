package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.config.OutboxProperties;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
class RabbitOutboxMessageSender implements OutboxMessagePublisher {
    private static final Map<String, String> ROUTING_KEYS = Map.of(
            "PaymentSucceeded", RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY,
            "RefundRequested", RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY,
            "RefundSucceeded", RabbitEventTopology.REFUND_SUCCEEDED_ROUTING_KEY,
            "RefundFailed", RabbitEventTopology.REFUND_FAILED_ROUTING_KEY);

    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;

    @Override
    public void publish(PaymentOutboxEvent event) throws Exception {
        String routingKey = ROUTING_KEYS.get(event.getEventType());
        if (routingKey == null) throw new IllegalArgumentException("不支持的 Outbox 事件类型: " + event.getEventType());
        rabbitTemplate.setMandatory(true);
        CorrelationData correlation = new CorrelationData(event.getEventId());
        rabbitTemplate.convertAndSend(RabbitEventTopology.DOMAIN_EXCHANGE, routingKey,
                event.getPayload(), message -> {
                    message.getMessageProperties().setMessageId(event.getEventId());
                    message.getMessageProperties().setContentType("application/json");
                    message.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setHeader("eventType", event.getEventType());
                    message.getMessageProperties().setHeader("eventVersion", event.getEventVersion());
                    return message;
                }, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(
                properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ publish nack: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ mandatory return: "
                    + correlation.getReturned().getReplyText());
        }
    }
}

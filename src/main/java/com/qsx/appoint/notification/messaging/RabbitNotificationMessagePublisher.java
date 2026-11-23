package com.qsx.appoint.notification.messaging;

import com.qsx.appoint.notification.application.NotificationMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ发布实现：持久化消息、publisher confirm和mandatory return。
 * <p>只有Broker确认且未退回时才算发布成功，否则Outbox保留并重试。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.notification.messaging-enabled", havingValue = "true")
class RabbitNotificationMessagePublisher implements NotificationMessagePublisher {
    private static final long CONFIRM_TIMEOUT_SECONDS = 10;

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(String eventId, String payload) throws Exception {
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), properties(eventId));
        CorrelationData correlation = new CorrelationData(eventId);
        rabbitTemplate.send(NotificationMessagingConfig.EXCHANGE, NotificationMessagingConfig.ROUTING_KEY,
                message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ未确认通知事件：" + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ无法路由通知事件：" + correlation.getReturned().getReplyText());
        }
    }

    private MessageProperties properties(String eventId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(eventId);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return properties;
    }
}

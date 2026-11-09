package com.qsx.appoint.common.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 只有 Broker 确认且消息可路由时才返回。 */
@Component
@RequiredArgsConstructor
public class ConfirmedMessagePublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publish(String exchange, String routingKey, Message message, Duration timeout) {
        rabbitTemplate.setMandatory(true);
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.send(exchange, routingKey, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("RabbitMQ publish nack: " + confirm.getReason());
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ mandatory return: " + correlation.getReturned().getReplyText());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ publish interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ publish confirmation failed", exception);
        }
    }
}

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

    /**
     * 在限定时间内等待Broker confirm并检查mandatory return；任一失败向上传播。
     * 超时/断连时Broker可能已接收，不能据此生成新业务事件ID；消费者ACK是另一阶段。
     * CorrelationData的随机ID只关联本次发送确认，不替换消息携带的业务eventId。
     * @param exchange 固定交换机
     * @param routingKey 固定路由
     * @param message 原业务消息或其传输属性克隆
     * @param timeout 等待确认的上限
     */
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

package com.qsx.appoint.coordination.messaging;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.application.PaymentSucceededEvent;
import com.qsx.appoint.coordination.application.PaymentSucceededProcessor;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** RabbitMQ 传输边界；业务事务成功返回后才 ACK。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.messaging.enabled", havingValue = "true")
public class PaymentSucceededListener {
    private final PaymentSucceededEventCodec codec;
    private final PaymentSucceededProcessor processor;

    @RabbitListener(queues = RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE, ackMode = "MANUAL")
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        PaymentSucceededEvent event;
        try {
            event = codec.read(message.getBody());
        } catch (BusinessException exception) {
            log.warn("拒绝不符合 PaymentSucceeded v1 契约的消息，messageId={}",
                    message.getMessageProperties().getMessageId());
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            processor.process(event);
            channel.basicAck(deliveryTag, false);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.VALIDATION_ERROR) {
                log.warn("拒绝永久无效的 PaymentSucceeded，eventId={}", event.eventId());
                channel.basicReject(deliveryTag, false);
            } else {
                log.warn("PaymentSucceeded 暂时处理失败并等待重投，eventId={}", event.eventId(), exception);
                channel.basicNack(deliveryTag, false, true);
            }
        } catch (RuntimeException exception) {
            log.warn("PaymentSucceeded 暂时处理失败并等待重投，eventId={}", event.eventId(), exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}

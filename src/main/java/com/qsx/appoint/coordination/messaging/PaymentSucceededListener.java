package com.qsx.appoint.coordination.messaging;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.messaging.ConfirmedMessagePublisher;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.application.PaymentSucceededEvent;
import com.qsx.appoint.coordination.application.PaymentSucceededProcessor;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/** RabbitMQ 传输边界；业务事务成功返回后才 ACK。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.messaging.enabled", havingValue = "true")
public class PaymentSucceededListener {
    private static final String ATTEMPT_HEADER = "payment-succeeded-attempt";
    private final ConfirmedMessagePublisher publisher;
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
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.VALIDATION_ERROR) {
                channel.basicReject(deliveryTag, false);
            } else {
                retryOrDeadLetter(message, channel, deliveryTag, event.eventId(), exception);
            }
            return;
        } catch (RuntimeException exception) {
            retryOrDeadLetter(message, channel, deliveryTag, event.eventId(), exception);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void retryOrDeadLetter(Message original, Channel channel, long tag,
                                   String eventId, RuntimeException exception) throws IOException {
        Integer current = original.getMessageProperties().getHeader(ATTEMPT_HEADER);
        int attempt = current == null ? 1 : current + 1;
        if (attempt >= 3) {
            log.error("支付成功事件处理达到最大次数，转入 DLQ，eventId={}", eventId, exception);
            channel.basicReject(tag, false);
            return;
        }
        Message retry = MessageBuilder.fromClonedMessage(original)
                .setHeader(ATTEMPT_HEADER, attempt).setExpiration("1000").build();
        try {
            publisher.publish(RabbitEventTopology.RETRY_EXCHANGE,
                    RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY, retry, Duration.ofSeconds(5));
        } catch (RuntimeException publishFailure) {
            log.error("支付成功事件重试转发失败，保留原消息，eventId={}", eventId, publishFailure);
            channel.basicNack(tag, false, true);
            return;
        }
        channel.basicAck(tag, false);
    }
}

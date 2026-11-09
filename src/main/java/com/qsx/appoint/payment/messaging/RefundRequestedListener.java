package com.qsx.appoint.payment.messaging;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.messaging.ConfirmedMessagePublisher;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.RefundRequestedEvent;
import com.qsx.appoint.payment.application.RefundWorker;
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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.messaging.enabled", havingValue = "true")
public class RefundRequestedListener {
    private static final int MAX_DELIVERIES = 3;
    private static final String ATTEMPT_HEADER = "refund-attempt";

    private final RefundRequestedEventCodec codec;
    private final RefundWorker worker;
    private final ConfirmedMessagePublisher publisher;

    @RabbitListener(queues = RabbitEventTopology.REFUND_WORKER_QUEUE, ackMode = "MANUAL")
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        RefundRequestedEvent event;
        try {
            event = codec.read(message.getBody());
        } catch (BusinessException exception) {
            channel.basicReject(tag, false);
            return;
        }
        try {
            worker.process(event);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.VALIDATION_ERROR) {
                channel.basicReject(tag, false);
            } else {
                retryOrDeadLetter(message, channel, tag, event.eventId(), exception);
            }
            return;
        } catch (RuntimeException exception) {
            retryOrDeadLetter(message, channel, tag, event.eventId(), exception);
            return;
        }
        channel.basicAck(tag, false);
    }

    private void retryOrDeadLetter(Message original, Channel channel, long tag,
                                   String eventId, RuntimeException exception) throws IOException {
        Integer current = original.getMessageProperties().getHeader(ATTEMPT_HEADER);
        int attempt = current == null ? 1 : current + 1;
        if (attempt >= MAX_DELIVERIES) {
            log.error("退款执行达到最大次数，转入 DLQ，eventId={}", eventId, exception);
            channel.basicReject(tag, false);
            return;
        }
        Message retry = MessageBuilder.fromClonedMessage(original)
                .setHeader(ATTEMPT_HEADER, attempt)
                .setExpiration("1000")
                .build();
        try {
            publisher.publish(RabbitEventTopology.RETRY_EXCHANGE,
                    RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY, retry, Duration.ofSeconds(5));
        } catch (RuntimeException publishFailure) {
            log.error("退款重试转发失败，保留原消息，eventId={}", eventId, publishFailure);
            channel.basicNack(tag, false, true);
            return;
        }
        channel.basicAck(tag, false);
    }
}

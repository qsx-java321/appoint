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

/**
 * 支付成功消息的传输入口，负责解码、手动ACK、失败分类及有界重试。
 * <p>业务交给带事务代理的Processor；先提交业务再ACK，确认不确定允许重投并由Inbox去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.messaging.enabled", havingValue = "true")
public class PaymentSucceededListener {
    private static final String ATTEMPT_HEADER = "payment-succeeded-attempt";
    private final ConfirmedMessagePublisher publisher;
    private final PaymentSucceededEventCodec codec;
    private final PaymentSucceededProcessor processor;

    /**
     * 消费业务消息：永久无效直接拒绝进DLQ，其余失败走有界重试，成功再ACK。
     * @param message 含正文和投递标签的原始Rabbit消息
     * @param channel 当前投递所属channel，ACK/NACK必须使用它的deliveryTag
     * @throws IOException ACK/NACK等传输操作失败，Broker可能重投；预约确认事务不因此撤销
     */
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
        // 业务处理已成功返回；提交后ACK前崩溃允许重投，由业务幂等保护。
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 累计当前业务处理次数，首次加两次一秒TTL重试，耗尽拒绝进DLQ。
     * <p>新重试转发必须confirm成功且无return后才ACK原消息；确认失败NACK/requeue保留原消息。
     * Broker不确定性可能造成额外投递，三次是业务重试策略，不是绝对物理投递上限。
     * @param original 原正文/消息属性，复制后保留eventId
     * @param eventId 日志关联事件ID，不生成新业务事件
     * @param exception 本次处理异常，用于记录失败原因
     * @throws IOException 原消息确认或拒绝失败
     */
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

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

/**
 * 退款请求消息监听器，把渠道执行结果转换为ACK、延迟重试或DLQ。
 * <p>worker负责本地分段事务；本监听器不把渠道调用包进消息数据库事务。
 */
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

    /**
     * 消费业务消息：永久无效直接拒绝进DLQ，其余失败走有界重试，成功再ACK。
     * @param message 含正文和投递标签的原始Rabbit消息
     * @param channel 当前投递所属channel，ACK/NACK必须使用它的deliveryTag
     * @throws IOException ACK/NACK等传输操作失败，Broker可能重投；退款worker本地结果不因此撤销
     */
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
        // 业务处理已成功返回；提交后ACK前崩溃允许重投，由业务幂等保护。
        channel.basicAck(tag, false);
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

package com.qsx.appoint.common.messaging;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Duration;

/** Rabbit 消费失败的传输处理：永久无效拒绝，暂时失败延迟重试；不持有业务事务。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitConsumerFailureHandler {
    private final ConfirmedMessagePublisher publisher;

    /**
     * 仅在业务/解码失败后调用。正常成功由监听器直接ACK，不增加重试头校验门槛。
     * 转发确认成功才ACK原投递；转发不确定则NACK保留。确认IOException必须向容器传播。
     * @param original 原消息，正文和ID必须保持
     * @param channel 原投递channel
     * @param tag 原投递deliveryTag
     * @param eventId 日志关联ID，可空
     * @param failure 本次执行失败
     * @param route 监听器固定装配的重试路由及计数头
     */
    public void handle(Message original, Channel channel, long tag, String eventId,
                       RuntimeException failure, RabbitRetryRoute route) throws IOException {
        if (failure instanceof BusinessException business && business.getErrorCode() == ErrorCode.VALIDATION_ERROR) {
            channel.basicReject(tag,false);
            return;
        }
        Object raw = original.getMessageProperties().getHeader(route.attemptHeader());
        long previous = raw == null ? 0 : (raw instanceof Integer || raw instanceof Long) ? ((Number)raw).longValue() : -1;
        // 先比较再递增，防止大计数溢出重新回到首次；非法头拒绝避免热重投。
        if (previous < 0 || previous > Integer.MAX_VALUE || previous >= 2) {
            log.error("消息失败转入DLQ，eventId={}, previousAttempts={}",eventId,raw,failure);
            channel.basicReject(tag,false);
            return;
        }
        Message retry = MessageBuilder.fromClonedMessage(original)
                .setHeader(route.attemptHeader(),(int)previous+1).setExpiration("1000").build();
        try {
            publisher.publish(route.exchange(),route.routingKey(),retry,Duration.ofSeconds(5));
        } catch (RuntimeException uncertain) {
            log.warn("重试转发未获确认，保留原消息，eventId={}",eventId,uncertain);
            channel.basicNack(tag,false,true);
            return;
        }
        // 放在publish的catch之外：ACK失败不能再被解释成需要转发新的重试消息。
        channel.basicAck(tag,false);
    }
}

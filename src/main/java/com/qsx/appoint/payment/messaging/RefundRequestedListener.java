package com.qsx.appoint.payment.messaging;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.messaging.RabbitConsumerFailureHandler;
import com.qsx.appoint.common.messaging.RabbitRetryRoute;
import com.qsx.appoint.payment.application.RefundRequestedEvent;
import com.qsx.appoint.payment.application.RefundWorker;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 退款请求消息监听器，把渠道执行结果转换为ACK、延迟重试或DLQ。
 * <p>worker负责本地分段事务；本监听器不把渠道调用包进消息数据库事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.messaging.enabled", havingValue = "true")
public class RefundRequestedListener {
    private static final RabbitRetryRoute RETRY = new RabbitRetryRoute(RabbitEventTopology.RETRY_EXCHANGE,
            RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY, "refund-attempt");

    private final RefundRequestedEventCodec codec;
    private final RefundWorker worker;
    private final RabbitConsumerFailureHandler failureHandler;

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
        } catch (RuntimeException exception) {
            failureHandler.handle(message, channel, tag, event.eventId(), exception, RETRY);
            return;
        }
        // 业务处理已成功返回；提交后ACK前崩溃允许重投，由业务幂等保护。
        channel.basicAck(tag, false);
    }

}

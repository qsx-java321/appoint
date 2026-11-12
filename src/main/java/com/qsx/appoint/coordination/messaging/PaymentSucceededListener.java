package com.qsx.appoint.coordination.messaging;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.messaging.RabbitConsumerFailureHandler;
import com.qsx.appoint.common.messaging.RabbitRetryRoute;
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

/**
 * 支付成功消息的传输入口，负责解码、手动ACK、失败分类及有界重试。
 * <p>业务交给带事务代理的Processor；先提交业务再ACK，确认不确定允许重投并由Inbox去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.messaging.enabled", havingValue = "true")
public class PaymentSucceededListener {
    private static final RabbitRetryRoute RETRY = new RabbitRetryRoute(RabbitEventTopology.RETRY_EXCHANGE,
            RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY, "payment-succeeded-attempt");
    private final RabbitConsumerFailureHandler failureHandler;
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
        } catch (RuntimeException exception) {
            failureHandler.handle(message, channel, deliveryTag, event.eventId(), exception, RETRY);
            return;
        }
        // 业务处理已成功返回；提交后ACK前崩溃允许重投，由业务幂等保护。
        channel.basicAck(deliveryTag, false);
    }

}

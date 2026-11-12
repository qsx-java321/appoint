package com.qsx.appoint.payment.messaging;

import com.qsx.appoint.payment.application.DeadLetterReplayService;
import com.qsx.appoint.payment.application.ReplayTarget;
import com.qsx.appoint.payment.application.ReplayResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.qsx.appoint.common.messaging.ConfirmedMessagePublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.payment.config.RabbitEventTopology;
import com.rabbitmq.client.GetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 受控DLQ重放用例，只允许本应用登记的队列，保持原消息正文及eventId。
 * <p>转发确认后才ACK原DLQ消息；失败NACK重入原队列。重放不是跳过幂等的重做操作。
 */
@Service
@RequiredArgsConstructor
public class RabbitDeadLetterReplayService implements DeadLetterReplayService {
    private static final Map<String, String> FIXED_ROUTES = Map.of(
            RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq",
            RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY,
            RabbitEventTopology.REFUND_WORKER_QUEUE + ".dlq",
            RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ConfirmedMessagePublisher publisher;

    /**
     * 从登记DLQ领取一条消息，清除重试计数/TTL后转发原文，确认成功才ACK原消息。
     * @param target 已登记的逻辑重放目标
     * @return EMPTY 或 REPLAYED；已重放正文缺 eventId 时保持 null，不补造ID
     * @throws IllegalArgumentException 未登记队列或退款结果事件类型不支持
     * @throws RuntimeException 解码/转发或确认失败，尝试NACK保留原消息
     */
    @Override
    public ReplayResult replayOne(ReplayTarget target) {
        if (target == null) throw new IllegalArgumentException("重放目标不能为空");
        String deadLetterQueue = switch (target) {
            case PAYMENT_SUCCEEDED -> RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq";
            case REFUND_REQUESTED -> RabbitEventTopology.REFUND_WORKER_QUEUE + ".dlq";
            case REFUND_RESULT -> RabbitEventTopology.REFUND_RESULT_QUEUE + ".dlq";
        };
        return rabbitTemplate.execute(channel -> {
            GetResponse delivery = channel.basicGet(deadLetterQueue, false);
            if (delivery == null) return new ReplayResult(ReplayResult.Status.EMPTY,null);
            try {
                JsonNode envelope = objectMapper.readTree(delivery.getBody());
                String eventId = envelope.path("eventId").asText(null);
                String routingKey = routingKey(deadLetterQueue, envelope.path("eventType").asText());
                var properties = new DefaultMessagePropertiesConverter().toMessageProperties(
                        delivery.getProps(), delivery.getEnvelope(), "UTF-8");
                properties.getHeaders().remove("payment-succeeded-attempt");
                properties.getHeaders().remove("refund-attempt");
                properties.setExpiration(null);
                publisher.publish(RabbitEventTopology.DOMAIN_EXCHANGE, routingKey,
                        new Message(delivery.getBody(), properties), Duration.ofSeconds(5));
                // 转发确认已返回；若确认不确定不能ACK丢掉原DLQ消息。
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                return new ReplayResult(ReplayResult.Status.REPLAYED,eventId);
            } catch (Exception exception) {
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                throw exception;
            }
        });
    }
    /** 根据已登记DLQ选择业务路由；退款结果队列再按成功/失败事件类型分流。 */
    private String routingKey(String queue, String eventType) {
        String fixed = FIXED_ROUTES.get(queue);
        if (fixed != null) return fixed;
        return switch (eventType) {
            case "RefundSucceeded" -> RabbitEventTopology.REFUND_SUCCEEDED_ROUTING_KEY;
            case "RefundFailed" -> RabbitEventTopology.REFUND_FAILED_ROUTING_KEY;
            default -> throw new IllegalArgumentException("退款结果 DLQ 中存在未知事件: " + eventType);
        };
    }
}

package com.qsx.appoint.payment.application;

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

/** 受控运维用例：仅允许重放本应用声明的 DLQ，并保留原事件正文/eventId。 */
@Service
@RequiredArgsConstructor
public class DeadLetterReplayService {
    private static final Map<String, String> FIXED_ROUTES = Map.of(
            RabbitEventTopology.PAYMENT_SUCCEEDED_QUEUE + ".dlq",
            RabbitEventTopology.PAYMENT_SUCCEEDED_ROUTING_KEY,
            RabbitEventTopology.REFUND_WORKER_QUEUE + ".dlq",
            RabbitEventTopology.REFUND_REQUESTED_ROUTING_KEY);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ConfirmedMessagePublisher publisher;

    /** @return 被重放的 eventId；队列为空返回 null。 */
    public String replayOne(String deadLetterQueue) {
        validateQueue(deadLetterQueue);
        return rabbitTemplate.execute(channel -> {
            GetResponse delivery = channel.basicGet(deadLetterQueue, false);
            if (delivery == null) return null;
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
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                return eventId;
            } catch (Exception exception) {
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                throw exception;
            }
        });
    }

    private void validateQueue(String queue) {
        if (!FIXED_ROUTES.containsKey(queue)
                && !(RabbitEventTopology.REFUND_RESULT_QUEUE + ".dlq").equals(queue)) {
            throw new IllegalArgumentException("不允许重放未登记的 DLQ: " + queue);
        }
    }

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

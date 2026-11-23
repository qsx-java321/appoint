package com.qsx.appoint.notification.messaging;

import com.qsx.appoint.notification.application.NotificationInboxWriter;
import com.qsx.appoint.notification.application.NotificationPayloadCodec;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 通知消费者：单并发、手动确认，首次投递加最多3次重试，仍失败则进入死信队列。
 * <p>重试在本次消费处理线程内按1秒、2秒、4秒退避；耗尽后只拒绝一次，由死信交换路由到DLQ。
 * 重复消息由写库唯一键幂等处理；无效消息重试耗尽后不回到主队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.notification.messaging-enabled", havingValue = "true")
class NotificationConsumer {
    private static final int MAX_DELIVERIES = 4;
    private static final long[] BACKOFF_MILLIS = {1000, 2000, 4000};

    private final NotificationInboxWriter inbox;
    private final NotificationPayloadCodec codec;

    @RabbitListener(queues = NotificationMessagingConfig.QUEUE)
    public void receive(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            processWithRetry(body);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            log.error("通知消息处理失败，转入死信队列，deliveryTag={}", deliveryTag, exception);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    void processWithRetry(String body) {
        RuntimeException lastFailure = null;
        for (int delivery = 1; delivery <= MAX_DELIVERIES; delivery++) {
            try {
                inbox.write(codec.decode(body));
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (delivery < MAX_DELIVERIES) pause(BACKOFF_MILLIS[delivery - 1]);
            }
        }
        throw lastFailure == null ? new IllegalStateException("通知消息处理失败") : lastFailure;
    }

    void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("通知消息重试被中断", exception);
        }
    }
}

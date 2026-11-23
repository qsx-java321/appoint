package com.qsx.appoint.notification.application;

import com.qsx.appoint.notification.entity.NotificationOutboxEvent;
import com.qsx.appoint.notification.mapper.NotificationOutboxEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 定时扫描待发布事件并投递到Broker；失败后指数退避，Broker恢复后继续排空。
 * <p>发布是至少一次语义，重复消息由消费端幂等兜底。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "appoint.notification.messaging-enabled", havingValue = "true")
public class NotificationOutboxPublisher {
    private static final int BATCH_SIZE = 50;
    private static final long MAX_BACKOFF_SECONDS = 60;

    private final NotificationOutboxEventMapper outbox;
    private final NotificationMessagePublisher publisher;
    private final Clock notificationClock;

    public NotificationOutboxPublisher(NotificationOutboxEventMapper outbox, NotificationMessagePublisher publisher,
                                       @Qualifier("notificationClock") Clock notificationClock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.notificationClock = notificationClock;
    }

    @Scheduled(fixedDelayString = "${appoint.notification.publish-delay:1s}")
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now(notificationClock).truncatedTo(ChronoUnit.SECONDS);
        for (NotificationOutboxEvent event : outbox.selectPending(now, BATCH_SIZE)) {
            int attempts = event.getAttempts() + 1;
            try {
                publisher.publish(event.getEventId(), event.getPayload());
                outbox.markPublished(event.getId(), attempts, now);
            } catch (Exception exception) {
                // next_attempt_at按秒存储；额外加1秒，保证实际等待不少于完整退避间隔。
                LocalDateTime nextAttemptAt = now.plusSeconds(backoffSeconds(attempts) + 1);
                outbox.markFailed(event.getId(), attempts, nextAttemptAt, summarize(exception));
                log.warn("通知事件发布失败，eventId={}, attempts={}, nextAttemptAt={}",
                        event.getEventId(), attempts, nextAttemptAt);
            }
        }
    }

    /** 首次失败等待1秒，随后指数增长，最大60秒。 */
    static long backoffSeconds(int attempts) {
        return Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(Math.max(attempts - 1, 0), 6));
    }

    private String summarize(Exception exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= 500 ? summary : summary.substring(0, 500);
    }
}

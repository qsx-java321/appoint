package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.config.OutboxProperties;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class OutboxPublishTransaction {
    private final PaymentOutboxEventMapper outboxMapper;
    private final OutboxMessagePublisher publisher;
    private final OutboxProperties properties;
    private final Clock appointmentClock;

    @Transactional
    public int publishDue() {
        LocalDateTime now = now();
        List<PaymentOutboxEvent> events = outboxMapper.selectDueForUpdate(now, properties.batchSize());
        int published = 0;
        for (PaymentOutboxEvent event : events) {
            try {
                publisher.publish(event);
                requireOne(outboxMapper.markPublished(event.getEventId(), now()));
                published++;
            } catch (Exception exception) {
                recordFailure(event, exception);
            }
        }
        return published;
    }

    private void recordFailure(PaymentOutboxEvent event, Exception exception) {
        int attempts = event.getPublishAttempts() + 1;
        boolean exhausted = attempts >= properties.maxAttempts();
        LocalDateTime nextAttemptAt = now().plus(backoff(attempts));
        String error = truncate(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        requireOne(outboxMapper.recordFailure(event.getEventId(), exhausted ? "FAILED" : "PENDING",
                attempts, nextAttemptAt, error));
        if (exhausted) {
            log.error("Outbox 发布重试耗尽，eventId={}, eventType={}, attempts={}, error={}",
                    event.getEventId(), event.getEventType(), attempts, error);
        } else {
            log.warn("Outbox 发布失败，等待重试，eventId={}, attempts={}, error={}",
                    event.getEventId(), attempts, error);
        }
    }

    private Duration backoff(int attempts) {
        int shift = Math.min(Math.max(attempts - 1, 0), 20);
        Duration candidate = properties.initialBackoff().multipliedBy(1L << shift);
        return candidate.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : candidate;
    }

    private String truncate(String value) {
        if (value == null) return "unknown publish failure";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void requireOne(int updated) {
        if (updated != 1) throw new IllegalStateException("Outbox 状态并发更新失败");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.MILLIS);
    }
}

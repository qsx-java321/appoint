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

/**
 * 在一段本地事务中SKIP LOCKED领取到期Outbox，等待Broker确认后记录发布状态。
 * <p>与支付业务事务分离；持锁发布用于避免并发领取。Broker和MySQL不能原子提交，
 * 确认后数据库提交前故障允许产生重复，消费者必须Inbox幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
class OutboxPublishTransaction {
    private final PaymentOutboxEventMapper outboxMapper;
    private final OutboxMessagePublisher publisher;
    private final OutboxProperties properties;
    private final Clock appointmentClock;

    /**
     * 锁定一批到期PENDING事件，逐条发布并记录成功或失败。
     * <p>SKIP LOCKED允许多个发布器领取不同事件；已发Broker但数据库提交不确定时可能重发。
     * @return 本批记录为PUBLISHED的数量，失败记录不计入
     */
    @Transactional
    public int publishDue() {
        LocalDateTime now = now();
        List<PaymentOutboxEvent> events = outboxMapper.selectDueForUpdate(now, properties.batchSize());
        int published = 0;
        for (PaymentOutboxEvent event : events) {
            try {
                publisher.publish(new OutboxMessage(event.getEventId(), event.getEventType(),
                        event.getEventVersion(), event.getPayload()));
            } catch (OutboxPublicationException exception) {
                recordFailure(event, exception);
                continue;
            }
            // 数据库写失败必须传播并回滚整批，不能误记为Broker失败；已发送消息允许原ID重投。
            requireOne(outboxMapper.markPublished(event.getEventId(), now()));
            published++;
        }
        return published;
    }

    /**
     * 累计发布失败并计算下次退避；达到配置上限置FAILED等待运维处理。
     * <p>这是Outbox发布重试，与消费者三次一秒重试是独立计数。
     */
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

    /**
     * 以初始退避乘2的幂计算延迟，指数和最大时长均封顶，避免溢出或无限增长。
     * @param attempts 已累计发布失败次数，从1开始
     * @return 下一次发布等待时长
     */
    private Duration backoff(int attempts) {
        int shift = Math.min(Math.max(attempts - 1, 0), 20);
        Duration candidate = properties.initialBackoff().multipliedBy(1L << shift);
        return candidate.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : candidate;
    }
    /** 将发布异常摘要限制在500字符，避免错误记录本身因字段超长而失败。 */
    private String truncate(String value) {
        if (value == null) return "unknown publish failure";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
    /** 发布状态写入必须命中一行；失败抛异常，不能静默丢失发布进度。 */
    private void requireOne(int updated) {
        if (updated != 1) throw new IllegalStateException("Outbox 状态并发更新失败");
    }
    /** 采样毫秒精度时间，与Outbox投递时间字段保持一致。 */
    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.MILLIS);
    }
}

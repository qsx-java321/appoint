package com.qsx.appoint.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Outbox发布与确认配置，独立于消费者三次一秒重试。
 * @param enabled 是否启用定时发布
 * @param batchSize 每批领取数量，非正值回退50
 * @param maxAttempts 发布失败上限，非正值回退8
 * @param initialBackoff 首次发布退避，未配置或非正值回退1秒
 * @param maxBackoff 退避上限，未配置或小于初始退避时回退5分钟
 * @param confirmTimeout 单次Broker确认等待时间，未配置或非正值回退5秒
 */
@ConfigurationProperties(prefix = "appoint.outbox")
public record OutboxProperties(
        boolean enabled,
        int batchSize,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration confirmTimeout) {

    public OutboxProperties {
        if (batchSize <= 0) batchSize = 50;
        if (maxAttempts <= 0) maxAttempts = 8;
        if (initialBackoff == null || initialBackoff.isNegative() || initialBackoff.isZero()) {
            initialBackoff = Duration.ofSeconds(1);
        }
        if (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
            maxBackoff = Duration.ofMinutes(5);
        }
        if (confirmTimeout == null || confirmTimeout.isNegative() || confirmTimeout.isZero()) {
            confirmTimeout = Duration.ofSeconds(5);
        }
    }
}

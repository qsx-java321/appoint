package com.qsx.appoint.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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

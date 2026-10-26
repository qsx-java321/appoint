package com.qsx.appoint.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "appoint.expiration")
public record ExpirationProperties(boolean enabled, Duration scanDelay, int batchSize) {
    public ExpirationProperties {
        if (scanDelay == null) scanDelay = Duration.ofSeconds(30);
        if (scanDelay.isZero() || scanDelay.isNegative()) {
            throw new IllegalArgumentException("appoint.expiration.scan-delay 必须大于 0");
        }
        if (batchSize <= 0) batchSize = 100;
    }
}

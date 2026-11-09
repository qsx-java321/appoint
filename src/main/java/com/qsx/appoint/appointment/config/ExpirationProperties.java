package com.qsx.appoint.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 预约过期扫描配置，扫描延迟不改变数据库支付截止资格。
 * @param enabled 是否启用定时扫描
 * @param scanDelay 扫描间隔，默认30秒且必须为正
 * @param batchSize 每批候选上限，非正值回退为100
 */
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

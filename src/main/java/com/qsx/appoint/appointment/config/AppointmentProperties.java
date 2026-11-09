package com.qsx.appoint.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 预约支付时限配置，前缀appoint.appointment。
 * @param paymentTimeout 从创建时刻计算的支付时限，未配置为15分钟；零或负值启动时拒绝
 */
@ConfigurationProperties(prefix = "appoint.appointment")
public record AppointmentProperties(Duration paymentTimeout) {
    public AppointmentProperties {
        if (paymentTimeout == null) paymentTimeout = Duration.ofMinutes(15);
        if (paymentTimeout.isZero() || paymentTimeout.isNegative()) {
            throw new IllegalArgumentException("appoint.appointment.payment-timeout 必须大于 0");
        }
    }
}

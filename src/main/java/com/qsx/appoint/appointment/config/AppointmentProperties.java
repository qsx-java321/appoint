package com.qsx.appoint.appointment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "appoint.appointment")
public record AppointmentProperties(Duration paymentTimeout) {
    public AppointmentProperties {
        if (paymentTimeout == null) paymentTimeout = Duration.ofMinutes(15);
        if (paymentTimeout.isZero() || paymentTimeout.isNegative()) {
            throw new IllegalArgumentException("appoint.appointment.payment-timeout 必须大于 0");
        }
    }
}

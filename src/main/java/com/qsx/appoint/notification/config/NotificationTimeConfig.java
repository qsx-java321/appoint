package com.qsx.appoint.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 通知事件、投递重试和入库时间使用真实时间，不复用可调的业务时钟。
 */
@Configuration(proxyBeanMethods = false)
public class NotificationTimeConfig {
    @Bean("notificationClock")
    public Clock notificationClock() {
        return Clock.systemDefaultZone();
    }
}

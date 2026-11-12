package com.qsx.appoint.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 每个测试上下文各有业务钟；使用者必须在每例开始时设置自己的上海本地时间。 */
@TestConfiguration(proxyBeanMethods = false)
public class AppointmentClockTestConfiguration {
    @Bean
    @Primary
    public MutableAppointmentClock testAppointmentClock() {
        return new MutableAppointmentClock("2026-11-05T12:00:00");
    }
}

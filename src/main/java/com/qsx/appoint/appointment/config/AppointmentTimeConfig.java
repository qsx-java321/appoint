package com.qsx.appoint.appointment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppointmentTimeConfig {
    @Bean
    public Clock appointmentClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}

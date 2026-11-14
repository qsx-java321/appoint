package com.qsx.appoint.localenv;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@Profile("test")
@ConditionalOnProperty(name = "appoint.local-environment.enabled", havingValue = "true")
public class TestClockConfig {
    @Bean @Primary AdjustableClock testBusinessClock() { return new AdjustableClock(ZoneId.of("Asia/Shanghai")); }
}

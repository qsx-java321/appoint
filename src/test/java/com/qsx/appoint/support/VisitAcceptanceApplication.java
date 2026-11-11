package com.qsx.appoint.support;

import com.qsx.appoint.AppointApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** 仅供隔离 HTTP 验收的测试启动入口，不打包到生产应用，不提供调时接口。 */
public class VisitAcceptanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[]{AppointApplication.class, TimeConfig.class}, args);
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary
        Clock acceptanceClock(@Value("${appoint.acceptance.instant}") String instant) {
            return Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Shanghai"));
        }
    }
}

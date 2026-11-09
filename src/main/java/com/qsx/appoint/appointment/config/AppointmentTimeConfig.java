package com.qsx.appoint.appointment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 提供Asia/Shanghai业务Clock，统一预约日期、截止和取消时间判断。
 * <p>业务层注入Clock，测试可替换为受控时间，不直接依赖宿主默认时区。
 */
@Configuration
public class AppointmentTimeConfig {
    /**
     * 创建统一的上海时区业务时钟。
     * @return 业务Clock，测试可替换以验证零点和截止边界
     */
    @Bean
    public Clock appointmentClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}

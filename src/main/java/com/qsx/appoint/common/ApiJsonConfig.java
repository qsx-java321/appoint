package com.qsx.appoint.common;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 浏览器无法安全表示雪花算法生成的 64 位 ID，因此 API 统一将 Long 响应序列化为字符串。 */
@Configuration
public class ApiJsonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longIdAsStringCustomizer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}

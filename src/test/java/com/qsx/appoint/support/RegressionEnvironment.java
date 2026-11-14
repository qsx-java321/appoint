package com.qsx.appoint.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 只屏蔽宿主环境连接覆盖，保留测试声明和 DynamicPropertySource 的容器注入。 */
public class RegressionEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var sources = context.getEnvironment().getPropertySources();
        sources.remove("spring.application.json");
        for (String name : new String[]{StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME}) {
            if (sources.get(name) instanceof MapPropertySource source) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                source.getSource().forEach((key, value) -> {
                    String normalized = key.toLowerCase(Locale.ROOT).replace('_', '.');
                    if (!normalized.startsWith("spring.") && !normalized.startsWith("appoint.")
                            && !normalized.startsWith("server.") && !normalized.startsWith("db.")
                            && !normalized.startsWith("mysql.") && !normalized.startsWith("rabbitmq.")
                            && !normalized.startsWith("redis.")) filtered.put(key, value);
                });
                sources.replace(name, new MapPropertySource(name, filtered));
            }
        }
    }
}

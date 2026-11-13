package com.qsx.appoint.catalog.config;

import com.qsx.appoint.catalog.cache.CatalogCacheProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CatalogCacheProperties.class)
public class CatalogCacheConfig {
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry catalogMeterRegistry() { return new SimpleMeterRegistry(); }
}

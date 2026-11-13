package com.qsx.appoint.catalog.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 缓存关闭时允许无 namespace；开启时拒绝不可清理的前缀与永久键配置。 */
@ConfigurationProperties("appoint.catalog-cache")
public record CatalogCacheProperties(boolean enabled, String namespace, @DefaultValue("5m") Duration hospitalTtl,
                                     @DefaultValue("5m") Duration departmentTtl, @DefaultValue("2m") Duration doctorTtl, @DefaultValue("30s") Duration emptyTtl) {
    public CatalogCacheProperties {
        if (enabled) {
            if (namespace == null || !namespace.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("目录缓存开启时必须配置合法 namespace");
            }
            for (Duration ttl : new Duration[]{hospitalTtl, departmentTtl, doctorTtl, emptyTtl}) {
                if (ttl == null || ttl.toMillis() <= 0) {
                    throw new IllegalArgumentException("目录缓存 TTL 必须至少为1毫秒");
                }
            }
        }
    }
}

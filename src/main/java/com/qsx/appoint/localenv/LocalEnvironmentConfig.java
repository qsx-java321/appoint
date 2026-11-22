package com.qsx.appoint.localenv;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class LocalEnvironmentConfig {
    @Bean static LocalEnvironmentLifecycle localEnvironmentLifecycle(ConfigurableEnvironment environment) {
        return new LocalEnvironmentLifecycle(environment);
    }

    @Bean
    @ConditionalOnProperty(name = "appoint.local-environment.enabled", havingValue = "true")
    FlywayMigrationStrategy localMigrationStrategy(LocalEnvironmentLifecycle lifecycle,
            StringRedisTemplate redis, DataSource source) {
        return flyway -> {
            if (lifecycle.reset()) {
                for (String prefix : java.util.List.of("appoint:" + lifecycle.name() + ":catalog:v1:*",
                        "appoint:" + lifecycle.name() + ":auth:*",
                        "appoint:" + lifecycle.name() + ":booking:*")) {
                    try (var cursor = redis.scan(ScanOptions.scanOptions().match(prefix).count(100).build())) {
                        cursor.forEachRemaining(redis::delete);
                    }
                }
                org.flywaydb.core.Flyway.configure().configuration(flyway.getConfiguration())
                        .cleanDisabled(false).load().clean();
            }
            flyway.migrate();
            var jdbc = new JdbcTemplate(source);
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
            transaction.executeWithoutResult(status -> lifecycle.seed(jdbc));
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "appoint.local-environment", name = {"enabled", "reset-only"}, havingValue = "true")
    ApplicationRunner finishReset(ConfigurableApplicationContext context) {
        return args -> context.close();
    }
}

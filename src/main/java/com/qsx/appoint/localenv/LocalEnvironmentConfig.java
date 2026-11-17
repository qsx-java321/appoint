package com.qsx.appoint.localenv;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
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
            StringRedisTemplate redis, ConnectionFactory rabbit, Declarables domainEventTopology, DataSource source,
            org.springframework.amqp.core.AmqpAdmin admin) {
        return flyway -> {
            if (lifecycle.reset()) {
                // 自身vhost内删除整个应用拓扑队列（含重试与DLQ），无全局管理权限。
                try (var connection = rabbit.createConnection(); var channel = connection.createChannel(false)) {
                    for (var item : domainEventTopology.getDeclarables()) {
                        if (item instanceof Queue queue) {
                            // 不存在的queueDelete按AMQP规范幂等成功。
                            channel.queueDelete(queue.getName());
                        }
                    }
                } catch (Exception e) { throw new IllegalStateException("环境消息重置失败；启动停止"); }
                for (String prefix : java.util.List.of("appoint:" + lifecycle.name() + ":catalog:v1:*",
                        "appoint:" + lifecycle.name() + ":auth:*")) {
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
            if (admin instanceof org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin) rabbitAdmin.initialize();
        };
    }

    @Bean
    @ConditionalOnProperty(name = "appoint.local-environment.reset-only", havingValue = "true")
    ApplicationRunner finishReset(ConfigurableApplicationContext context, LocalEnvironmentLifecycle lifecycle) {
        return args -> {
            if (!"test".equals(lifecycle.name())) throw new IllegalStateException("只允许显式重置test");
            context.close();
        };
    }
}

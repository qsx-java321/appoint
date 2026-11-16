package com.qsx.appoint.support;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlContainerConfiguration {
    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer(
            @Value("${it.mysql.image:mysql@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb}") String image) {
        return new MySQLContainer<>(image)
                .withDatabaseName("appoint_it")
                .withUsername("it_user")
                .withPassword(UUID.randomUUID().toString())
                .withEnv("TZ", "Asia/Shanghai")
                .withCommand("--default-time-zone=+08:00")
                .withUrlParam("serverTimezone", "Asia/Shanghai")
                .withUrlParam("socketTimeout", "20000")
                .withStartupTimeout(Duration.ofMinutes(2));
    }
}

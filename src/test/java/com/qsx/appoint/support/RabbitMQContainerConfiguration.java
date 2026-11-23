package com.qsx.appoint.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/** 为真实RabbitMQ端到端测试提供隔离Broker；镜像与本地Compose保持同一版本线。 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitMQContainerConfiguration {
    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer(
            @Value("${it.rabbitmq.image:rabbitmq:4.3.2-management}") String image) {
        return new RabbitMQContainer(DockerImageName.parse(image));
    }
}

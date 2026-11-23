package com.qsx.appoint.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 通知Topic拓扑与死信拓扑；只在启用消息投递的档位声明。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "appoint.notification.messaging-enabled", havingValue = "true")
public class NotificationMessagingConfig {
    public static final String EXCHANGE = "appoint.notification.exchange";
    public static final String QUEUE = "appoint.notification.queue";
    public static final String ROUTING_KEY = "appointment.notification";
    public static final String DEAD_LETTER_EXCHANGE = "appoint.notification.dlx";
    public static final String DEAD_LETTER_QUEUE = "appoint.notification.dlq";

    @Bean
    TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(ROUTING_KEY);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue,
                                          DirectExchange notificationDeadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(notificationDeadLetterExchange).with(ROUTING_KEY);
    }
}

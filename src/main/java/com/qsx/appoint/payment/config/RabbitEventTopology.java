package com.qsx.appoint.payment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RabbitEventTopology {
    public static final String DOMAIN_EXCHANGE = "appoint.domain-events";
    public static final String RETRY_EXCHANGE = "appoint.domain-events.retry";
    public static final String DEAD_LETTER_EXCHANGE = "appoint.domain-events.dlx";

    public static final String PAYMENT_SUCCEEDED_ROUTING_KEY = "payment.succeeded.v1";
    public static final String REFUND_REQUESTED_ROUTING_KEY = "payment.refund.requested.v1";
    public static final String REFUND_SUCCEEDED_ROUTING_KEY = "payment.refund.succeeded.v1";
    public static final String REFUND_FAILED_ROUTING_KEY = "payment.refund.failed.v1";

    public static final String PAYMENT_SUCCEEDED_QUEUE = "appointment.payment-succeeded.v1";
    public static final String REFUND_WORKER_QUEUE = "payment.refund-worker.v1";
    public static final String REFUND_RESULT_QUEUE = "notification.refund-result.v1";

    @Bean
    Declarables domainEventTopology() {
        TopicExchange domain = new TopicExchange(DOMAIN_EXCHANGE, true, false);
        TopicExchange retry = new TopicExchange(RETRY_EXCHANGE, true, false);
        TopicExchange dlx = new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
        List<Declarable> declarables = new ArrayList<>(List.of(domain, retry, dlx));
        addQueue(declarables, domain, retry, dlx, PAYMENT_SUCCEEDED_QUEUE,
                PAYMENT_SUCCEEDED_ROUTING_KEY);
        addQueue(declarables, domain, retry, dlx, REFUND_WORKER_QUEUE,
                REFUND_REQUESTED_ROUTING_KEY);
        addQueue(declarables, domain, retry, dlx, REFUND_RESULT_QUEUE,
                REFUND_SUCCEEDED_ROUTING_KEY, REFUND_FAILED_ROUTING_KEY);
        return new Declarables(declarables);
    }

    private void addQueue(List<Declarable> declarables, TopicExchange domain, TopicExchange retry,
                          TopicExchange dlx, String queueName, String... routingKeys) {
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(queueName)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(queueName + ".dlq").build();
        declarables.add(queue);
        declarables.add(deadLetterQueue);
        for (int index = 0; index < routingKeys.length; index++) {
            String routingKey = routingKeys[index];
            String retryQueueName = queueName + ".retry" + (routingKeys.length == 1 ? "" : "." + (index + 1));
            Queue retryQueue = QueueBuilder.durable(retryQueueName)
                    .deadLetterExchange(DOMAIN_EXCHANGE)
                    .deadLetterRoutingKey(routingKey)
                    .build();
            declarables.add(retryQueue);
            Binding businessBinding = BindingBuilder.bind(queue).to(domain).with(routingKey);
            Binding retryBinding = BindingBuilder.bind(retryQueue).to(retry).with(routingKey);
            declarables.add(businessBinding);
            declarables.add(retryBinding);
        }
        declarables.add(BindingBuilder.bind(deadLetterQueue).to(dlx).with(queueName));
    }
}

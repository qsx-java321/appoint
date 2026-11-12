package com.qsx.appoint.support;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 仅在类独占的Rabbit/MySQL容器中使用；停止并等候监听结束，再清理本例数据和固定队列。 */
public final class RabbitReliabilityFixtures {
    private final ApplicationContext context;
    private final JdbcTemplate jdbc;
    private final List<org.springframework.amqp.rabbit.listener.MessageListenerContainer> listeners;
    private CommittedFixtureScope scope;

    public RabbitReliabilityFixtures(ApplicationContext context, JdbcTemplate jdbc) {
        this.context = context;
        this.jdbc = jdbc;
        listeners = context.getBeansOfType(RabbitListenerEndpointRegistry.class).values().stream()
                .flatMap(registry -> registry.getListenerContainers().stream()).toList();
    }

    public void beforeEach() throws InterruptedException {
        stopListeners();
        try {
            purgeQueues();
            scope = new CommittedFixtureScope(jdbc);
        } finally { startListeners(); }
    }

    public void afterEach() throws InterruptedException {
        stopListeners();
        try {
            if (scope != null) scope.close();
            purgeQueues();
        } finally { startListeners(); }
    }

    public void stopListeners() throws InterruptedException {
        var stopped = new CountDownLatch(listeners.size());
        listeners.forEach(listener -> listener.stop(stopped::countDown));
        if (!stopped.await(30, TimeUnit.SECONDS)) throw new AssertionError("监听器30秒内未结束，拒绝清理在途数据");
    }

    private void startListeners() { listeners.forEach(org.springframework.amqp.rabbit.listener.MessageListenerContainer::start); }

    private void purgeQueues() {
        // 先清重试队列，避免清理过程中TTL消息再次投向已清空的业务队列。
        var queues = context.getBean("domainEventTopology", Declarables.class).getDeclarablesByType(Queue.class).stream()
                .sorted(java.util.Comparator.comparing(queue -> !queue.getName().contains(".retry"))).toList();
        context.getBean(RabbitTemplate.class).execute(channel -> {
            for (Queue queue : queues) channel.queuePurge(queue.getName());
            return null;
        });
    }
}

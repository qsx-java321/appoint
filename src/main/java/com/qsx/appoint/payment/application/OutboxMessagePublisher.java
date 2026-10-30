package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.entity.PaymentOutboxEvent;

/** 将一条已持久化事件可靠写入 Broker；数据库发布状态由调用方另行提交。 */
public interface OutboxMessagePublisher {
    void publish(PaymentOutboxEvent event) throws Exception;
}

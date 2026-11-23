package com.qsx.appoint.notification.application;

/** 通知投递端口；生产实现使用RabbitMQ，测试可替换为可控失败实现。 */
public interface NotificationMessagePublisher {
    /**
     * 发布一条已持久化的Outbox事件。
     * @param eventId 事件UUID，同时作为消息ID
     * @param payload 已渲染的通知JSON
     * @throws Exception Broker不可用、确认失败或路由失败
     */
    void publish(String eventId, String payload) throws Exception;
}

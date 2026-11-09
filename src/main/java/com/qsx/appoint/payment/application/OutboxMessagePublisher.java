package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.entity.PaymentOutboxEvent;

/**
 * 支付模块内部的Broker发布seam；接收持久化事件，不负责修改Outbox数据库状态。
 * <p>生产Adapter只有在发布确认且无mandatory return时正常返回，失败由外层记录重试。
 */
public interface OutboxMessagePublisher {
    /**
     * 可靠转发一条已提交的Outbox事件，并等待Broker确认结果。
     * @param event 含固定eventId、类型、v1正文的持久化事件
     * @throws Exception 不支持的类型、路由return、NACK、超时或连接故障；调用者不能标记已发布
     */
    void publish(PaymentOutboxEvent event) throws Exception;
}

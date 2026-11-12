package com.qsx.appoint.payment.application;


/**
 * 支付模块内部的Broker发布seam；接收不可变消息快照，不负责修改Outbox数据库状态。
 * <p>生产Adapter只有在发布确认且无mandatory return时正常返回，失败由外层记录重试；超时可能已经送达，正常返回不表示消费完成。
 */
public interface OutboxMessagePublisher {
    /**
     * 可靠转发一条已提交的Outbox事件，并等待Broker确认结果。
     * @param event 含固定eventId、类型、v1正文的持久化事件
     * @throws OutboxPublicationException 不支持的类型、路由return、NACK、超时或连接故障；调用者不能标记已发布
     */
    void publish(OutboxMessage event) throws OutboxPublicationException;
}

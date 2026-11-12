package com.qsx.appoint.coordination.application;

/**
 * 支付成功事件的事务业务入口；传输监听器在代理调用成功返回后才ACK。
 * <p>Inbox与预约确认/关闭/退款请求同事务提交，重复投递按eventId幂等。
 */
public interface PaymentSucceededProcessor {
    /**
     * 处理已解码的PaymentSucceeded v1，确认预约或进入关闭退款补偿。
     * @param event 成功收款事实，eventId须为UUID、金额为正且币种为CNY
     * @return DUPLICATE、ALREADY_CONFIRMED、CONFIRMED或REFUND_REQUIRED；后者不代表渠道退款已完成
     * @throws com.qsx.appoint.common.BusinessException 契约无效为VALIDATION_ERROR；关联或写入不满足预期为完整性错误
     */
    PaymentSucceededResult process(PaymentSucceededEvent event);
}

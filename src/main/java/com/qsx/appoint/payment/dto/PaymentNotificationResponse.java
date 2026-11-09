package com.qsx.appoint.payment.dto;

/**
 * 渠道通知处理摘要；正常返回不代表一定收款，需检查processingResult。
 * @param notificationId 渠道通知标识
 * @param processingResult 审计结果，如SUCCEEDED、FAILED、拒绝或幂等结果
 * @param paymentOrderId 可定位的支付ID；未知订单或快速重复短路可为null
 * @param paymentStatus 本次处理后的资金状态；重复或未知订单时可为null
 * @param duplicate 是否返回已经保存的同通知审计
 * @param eventId 本次新建成功事件ID；无新事件或重复短路时为null
 */
public record PaymentNotificationResponse(
        String notificationId,
        String processingResult,
        Long paymentOrderId,
        String paymentStatus,
        boolean duplicate,
        String eventId) {
}

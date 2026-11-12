package com.qsx.appoint.payment.contract.event;


/** RefundFailedPayload 的 v1 消息结构；字段名与历史消息一致，Long 沿用生产序列化配置。 */
public record RefundFailedPayload(Long refundId, String refundNo, Long paymentOrderId, String providerRefundNo, String failureMessage) {}

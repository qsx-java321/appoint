package com.qsx.appoint.payment.contract.event;

import java.math.BigDecimal;

/** RefundRequestedPayload 的 v1 消息结构；字段名与历史消息一致，Long 沿用生产序列化配置。 */
public record RefundRequestedPayload(Long refundId, String refundNo, Long paymentOrderId, Long paymentAttemptId, String providerTransactionNo, BigDecimal amount, String currency, String channel, String reason) {}

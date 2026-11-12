package com.qsx.appoint.payment.contract.event;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/** PaymentSucceededPayload 的 v1 消息结构；字段名与历史消息一致，Long 沿用生产序列化配置。 */
public record PaymentSucceededPayload(Long paymentOrderId, Long appointmentOrderId, Long paymentAttemptId, String providerTransactionNo, BigDecimal amount, String currency, String channel, LocalDateTime paidAt) {}

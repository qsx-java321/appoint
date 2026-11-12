package com.qsx.appoint.payment.contract.event;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * PaymentSucceededPayload 的稳定v1消息结构，沿用生产Long文本和时间配置。
 * @param paymentOrderId 资金事实所属支付单ID
 * @param appointmentOrderId 需要确认或补偿的预约单ID
 * @param paymentAttemptId 对应支付尝试ID
 * @param providerTransactionNo 已成功收款的渠道流水号
 * @param amount 原支付金额，单位元
 * @param currency 币种，目前CNY
 * @param channel 稳定渠道名称
 * @param paidAt 服务端确认收款时间，严格早于支付截止才具备确认资格
 */
public record PaymentSucceededPayload(Long paymentOrderId, Long appointmentOrderId, Long paymentAttemptId, String providerTransactionNo, BigDecimal amount, String currency, String channel, LocalDateTime paidAt) {}

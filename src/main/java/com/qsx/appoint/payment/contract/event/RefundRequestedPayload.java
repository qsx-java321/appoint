package com.qsx.appoint.payment.contract.event;

import java.math.BigDecimal;

/**
 * RefundRequestedPayload 的稳定v1消息结构，沿用生产Long文本和时间配置。
 * @param refundId 本地退款单ID
 * @param refundNo 本地退款编号，也是渠道退款幂等依据
 * @param paymentOrderId 资金事实所属支付单ID
 * @param paymentAttemptId 对应支付尝试ID
 * @param providerTransactionNo 已成功收款的渠道流水号
 * @param amount 原支付金额，单位元
 * @param currency 币种，目前CNY
 * @param channel 稳定渠道名称
 * @param reason 创建退款时保存的原因，不在重试时改写
 */
public record RefundRequestedPayload(Long refundId, String refundNo, Long paymentOrderId, Long paymentAttemptId, String providerTransactionNo, BigDecimal amount, String currency, String channel, String reason) {}

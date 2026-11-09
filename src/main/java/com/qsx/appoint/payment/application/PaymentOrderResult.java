package com.qsx.appoint.payment.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 同库支付建单后的稳定结果，用于首次预约响应。
 * @param paymentOrderId 新支付单ID
 * @param paymentNo 支付业务编号
 * @param status 创建时UNPAID，不表示后续实时状态
 * @param amount 应付金额快照，单位元
 * @param currency 币种，当前CNY
 * @param expiresAt 支付截止，与预约快照一致
 */
public record PaymentOrderResult(
        Long paymentOrderId,
        String paymentNo,
        String status,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt) {
}


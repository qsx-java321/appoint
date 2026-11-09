package com.qsx.appoint.payment.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 从可信预约暂占复制的支付建单命令，不重新按当前设置计费。
 * @param appointmentOrderId 预约单ID，与支付单一一对应
 * @param userId 预约所属账号ID
 * @param amount 应付金额快照，单位元
 * @param currency 币种快照，当前CNY
 * @param expiresAt 与预约一致的支付截止
 * @param createdAt 与预约一致的创建时间
 */
public record CreatePaymentOrderCommand(
        Long appointmentOrderId,
        Long userId,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {
}


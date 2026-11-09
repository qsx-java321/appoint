package com.qsx.appoint.payment.dto;

import com.qsx.appoint.payment.domain.PaymentChannel;

import java.time.LocalDateTime;

/**
 * 支付尝试建立后的HTTP202结果，最终资金状态需通过通知/查询确认。
 * @param paymentOrderId 支付单ID
 * @param paymentAttemptId 本地尝试ID
 * @param channel 支付渠道
 * @param paymentStatus 本次下单结果中的支付状态，通常PAYING
 * @param attemptStatus 本次尝试状态，通常PROCESSING
 * @param providerOrderNo 渠道订单号，不是本地attemptNo
 * @param checkoutUrl 渠道会话入口；MockPay后接/complete可模拟支付
 * @param expiresAt 支付截止，不因查询或重试延长
 */
public record PaymentAttemptResponse(
        Long paymentOrderId,
        Long paymentAttemptId,
        PaymentChannel channel,
        String paymentStatus,
        String attemptStatus,
        String providerOrderNo,
        String checkoutUrl,
        LocalDateTime expiresAt) {
}

package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * T1提交后交给事务外渠道下单的不可变工作快照，仅在payment内部使用。
 * @param paymentOrderId 支付单ID
 * @param paymentNo 本地支付业务编号
 * @param attemptId T1新建的attempt主键
 * @param attemptNo 渠道下单幂等编号，区别于attempt主键
 * @param channel 已注册渠道
 * @param amount 支付金额快照，单位元
 * @param currency 币种快照
 * @param expiresAt 本地支付截止
 */
record CreatedPaymentAttempt(
        Long paymentOrderId,
        String paymentNo,
        Long attemptId,
        String attemptNo,
        PaymentChannel channel,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt) {
}

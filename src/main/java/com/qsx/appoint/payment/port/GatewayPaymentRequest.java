package com.qsx.appoint.payment.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 事务外渠道下单请求，attemptNo承担本次尝试的幂等身份。
 * @param paymentNo 本地支付业务编号
 * @param attemptNo 本次尝试编号，同号重试内容必须相同
 * @param amount 应付金额快照，单位元
 * @param currency 币种代码，当前CNY
 * @param expiresAt 本地支付截止，渠道实现不应据重试延长
 */
public record GatewayPaymentRequest(
        String paymentNo,
        String attemptNo,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt) {
}

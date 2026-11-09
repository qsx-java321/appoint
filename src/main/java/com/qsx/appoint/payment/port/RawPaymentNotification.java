package com.qsx.appoint.payment.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 未经信任的原始渠道通知，必须交对应Gateway验签后才能处理资金。
 * @param notificationId 渠道通知幂等标识，与channel共同隔离
 * @param providerOrderNo 渠道支付订单号
 * @param providerTransactionNo 成功交易流水；失败通知可能为null
 * @param status 渠道声明结果，验签前不可信
 * @param amount 渠道声明金额，单位元，业务仍须比较快照
 * @param currency 渠道声明币种，业务仍须比较快照
 * @param occurredAt 渠道声明通知时间，不能代替系统paidAt
 * @param signature 渠道签名，不应写入日志或长期审计正文
 */
public record RawPaymentNotification(
        String notificationId,
        String providerOrderNo,
        String providerTransactionNo,
        GatewayPaymentStatus status,
        BigDecimal amount,
        String currency,
        LocalDateTime occurredAt,
        String signature) {
}

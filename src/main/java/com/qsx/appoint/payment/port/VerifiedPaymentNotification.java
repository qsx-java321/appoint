package com.qsx.appoint.payment.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Gateway验签后交给事务服务的可信字段集合，不携带签名。
 * <p>验签只证明报文来源与完整性；金额、关联、幂等和状态仍须业务层复验。
 * @param notificationId 原通知标识
 * @param providerOrderNo 用于定位本地attempt的渠道订单号
 * @param providerTransactionNo 原成功收款流水，失败通知可为null
 * @param status 已验签的渠道声明结果
 * @param amount 已验签金额，仍与支付单快照比较
 * @param currency 已验签币种
 * @param occurredAt 渠道时间，仅保留事实，不用于替代本系统资金确认时间
 */
public record VerifiedPaymentNotification(
        String notificationId,
        String providerOrderNo,
        String providerTransactionNo,
        GatewayPaymentStatus status,
        BigDecimal amount,
        String currency,
        LocalDateTime occurredAt) {
}

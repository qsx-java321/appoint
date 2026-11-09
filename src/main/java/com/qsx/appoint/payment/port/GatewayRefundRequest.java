package com.qsx.appoint.payment.port;

import java.math.BigDecimal;

/**
 * 事务外原路全额退款命令，refundNo必须跨重试保持不变。
 * @param refundNo 本地退款编号/渠道幂等键，同号异内容须拒绝
 * @param providerTransactionNo 原成功收款的渠道流水
 * @param amount 全额退款金额，单位元
 * @param currency 原收款币种
 * @param reason 本地RefundReason稳定名称
 */
public record GatewayRefundRequest(
        String refundNo,
        String providerTransactionNo,
        BigDecimal amount,
        String currency,
        String reason) {
}

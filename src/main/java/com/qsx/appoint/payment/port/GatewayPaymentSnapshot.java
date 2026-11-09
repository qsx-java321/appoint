package com.qsx.appoint.payment.port;

import java.math.BigDecimal;

/**
 * 渠道查询结果，描述渠道侧资金事实，不直接修改预约或支付数据库。
 * @param providerOrderNo 被查询的渠道订单号
 * @param providerTransactionNo 成功渠道流水，未成功时可为null
 * @param status 查询时渠道状态
 * @param amount 渠道订单金额，单位元
 * @param currency 渠道订单币种
 */
public record GatewayPaymentSnapshot(
        String providerOrderNo,
        String providerTransactionNo,
        GatewayPaymentStatus status,
        BigDecimal amount,
        String currency) {
}

package com.qsx.appoint.payment.port;

/**
 * 渠道退款返回结果；当前worker只把SUCCEEDED视为退款完成。
 * @param providerRefundNo 渠道退款编号，渠道尚未成功时可能为空
 * @param status 渠道执行状态，PROCESSING不等于成功，当前worker会按失败重试处理
 */
public record GatewayRefundSubmission(String providerRefundNo, GatewayRefundStatus status) {
}

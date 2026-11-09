package com.qsx.appoint.payment.port;

/**
 * 渠道关闭订单命令，按三个编号复验同一支付尝试。
 * <p>当前生命周期closeUnpaid只关闭本地支付单，不调用此能力。
 * @param paymentNo 本地支付单业务编号
 * @param attemptNo 本地尝试幂等编号
 * @param providerOrderNo 目标渠道订单号
 */
public record GatewayCloseRequest(String paymentNo, String attemptNo, String providerOrderNo) {
}

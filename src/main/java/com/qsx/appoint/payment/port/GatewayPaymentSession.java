package com.qsx.appoint.payment.port;

/**
 * 渠道下单返回的会话，不是最终资金成功事实。
 * @param providerOrderNo 渠道生成或幂等复用的支付订单号
 * @param status 渠道当前状态，发起用例当前只接受PROCESSING
 * @param checkoutUrl 交给客户端继续支付的入口
 */
public record GatewayPaymentSession(
        String providerOrderNo,
        GatewayPaymentStatus status,
        String checkoutUrl) {
}

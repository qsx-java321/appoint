package com.qsx.appoint.payment.port;

/** 渠道侧支付状态，不直接作为本地支付单状态写入。 */
public enum GatewayPaymentStatus {
    /** 渠道订单处理中。 */
    PROCESSING,
    /** 渠道报告收款成功。 */
    SUCCEEDED,
    /** 渠道报告失败。 */
    FAILED,
    /** 渠道订单已关闭。 */
    CLOSED
}

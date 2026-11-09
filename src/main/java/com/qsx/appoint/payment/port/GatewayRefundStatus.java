package com.qsx.appoint.payment.port;

/** 渠道侧退款结果；当前worker只接受SUCCEEDED为完成。 */
public enum GatewayRefundStatus {
    /** 渠道尚在处理，当前worker按未确认成功进行失败重试。 */
    PROCESSING,
    /** 渠道确认退款成功。 */
    SUCCEEDED,
    /** 渠道退款失败。 */
    FAILED
}

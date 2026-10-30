package com.qsx.appoint.payment.domain;

/** 支付渠道稳定标识，不暴露具体渠道 SDK 类型。 */
public enum PaymentChannel {
    MOCK,
    WECHAT_PAY,
    ALIPAY
}


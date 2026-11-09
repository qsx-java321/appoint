package com.qsx.appoint.payment.domain;

/** 渠道稳定枚举，隔离SDK类型；当前只有MOCK在dev/test有Adapter。 */
public enum PaymentChannel {
    /** 开发/测试模拟渠道，不在生产注册。 */
    MOCK,
    /** 预留微信支付标识，尚未接入真实渠道。 */
    WECHAT_PAY,
    /** 预留支付宝标识，尚未接入真实渠道。 */
    ALIPAY
}


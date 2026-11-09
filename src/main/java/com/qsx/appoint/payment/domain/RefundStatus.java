package com.qsx.appoint.payment.domain;

/** 退款单执行状态，不等同支付单资金退款状态或消息投递状态。 */
public enum RefundStatus {
    /** 申请已落库，等待执行或重试。 */
    PENDING,
    /** 渠道成功结果已在本地提交。 */
    SUCCEEDED,
    /** 一次渠道执行失败，可能仍可重试或等待DLQ运维。 */
    FAILED
}


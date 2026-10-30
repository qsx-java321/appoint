package com.qsx.appoint.payment.domain;

/** 一次渠道支付尝试的处理状态。 */
public enum PaymentAttemptStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CLOSED
}


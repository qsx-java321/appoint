package com.qsx.appoint.payment.domain;

/** 支付单整体资金状态。 */
public enum PaymentOrderStatus {
    UNPAID,
    PAYING,
    PAID,
    CLOSED,
    REFUND_PENDING,
    REFUNDED
}


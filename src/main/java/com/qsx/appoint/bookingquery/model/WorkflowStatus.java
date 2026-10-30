package com.qsx.appoint.bookingquery.model;

/** 预约与支付组合后的用户可读流程状态，不是预约单领域状态。 */
public enum WorkflowStatus {
    WAITING_FOR_PAYMENT,
    PAYMENT_PROCESSING,
    CONFIRMING_APPOINTMENT,
    CONFIRMED,
    CANCELLED_REFUNDING,
    CANCELLED_REFUNDED,
    EXPIRED
}

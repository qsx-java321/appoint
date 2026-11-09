package com.qsx.appoint.bookingquery.model;

/** 预约+支付的用户可见流程状态，只由只读Projector计算，不是独立写状态机。 */
public enum WorkflowStatus {
    /** 待支付预约+UNPAID。 */
    WAITING_FOR_PAYMENT,
    /** 待支付预约+PAYING。 */
    PAYMENT_PROCESSING,
    /** 资金已收款但预约确认事务尚未完成。 */
    CONFIRMING_APPOINTMENT,
    /** 预约已确认且支付PAID。 */
    CONFIRMED,
    /** 任一关闭终态+REFUND_PENDING，包含过期退款。 */
    CANCELLED_REFUNDING,
    /** 任一关闭终态+REFUNDED。 */
    CANCELLED_REFUNDED,
    /** 预约EXPIRED且未收款支付CLOSED。 */
    EXPIRED
}

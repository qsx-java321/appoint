package com.qsx.appoint.payment.domain;

/** 首次退款申请的稳定原因，数据库/事件保存同名字符串，幂等重复申请不覆盖。 */
public enum RefundReason {
    /** 用户取消导致退款，含已付款待确认时取消。 */
    APPOINTMENT_CANCELLED,
    /** 支付单已关闭后成功通知记账导致的迟到退款。 */
    LATE_PAYMENT,
    /** 成功事件或已付对账判断无法确认预约而退款。 */
    APPOINTMENT_NOT_CONFIRMABLE,
    /** 对账为已关闭仍PAID预约补齐退款。 */
    RECONCILIATION_CLOSED_PAID
}

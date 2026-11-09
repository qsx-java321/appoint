package com.qsx.appoint.appointment.domain;

/** 待支付预约关闭原因，显式映射目标状态，避免调用方传入任意状态字符串。 */
public enum AppointmentCloseReason {
    /** 未到支付截止的主动取消，映射CANCELLED。 */
    USER_CANCELLED(AppointmentStatus.CANCELLED),
    /** 支付期限已到，映射EXPIRED。 */
    PAYMENT_EXPIRED(AppointmentStatus.EXPIRED);
    private final AppointmentStatus status;
    AppointmentCloseReason(AppointmentStatus status) { this.status = status; }
    /** 返回该关闭原因允许写入的预约终态，调用方不能用它表达确认或任意状态跳转。 */
    public AppointmentStatus status() { return status; }
}

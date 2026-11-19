package com.qsx.appoint.coordination.application;

/** 预约付款、取消和过期的同库事务入口。 */
public interface BookingLifecycleCoordinator {
    /** 同步确认本人待支付预约。已支付的确认结果可以重放。 */
    void payOwned(Long userId, Long appointmentOrderId);

    /** 取消本人未付款预约，释放暂占。 */
    void cancelOwned(Long userId, Long appointmentOrderId);

    /** 逐单锁后复验，并关闭已超过最晚付款时间的未付款预约。 */
    boolean expireIfDue(Long appointmentOrderId);
}

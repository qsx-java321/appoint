package com.qsx.appoint.payment.application;

public interface PaymentLifecycle {
    PaymentLifecycleCase find(Long paymentOrderId);
    PaymentLifecycleCase lockByAppointment(Long appointmentOrderId);
    void closeUnpaid(PaymentLifecycleCase payment);
    /** 仅供待08删除的dev/test同步兼容路径使用。 */
    void markPaid(Long paymentOrderId);
}

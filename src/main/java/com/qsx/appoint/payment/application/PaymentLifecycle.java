package com.qsx.appoint.payment.application;

public interface PaymentLifecycle {
    PaymentLifecycleCase find(Long paymentOrderId);
    PaymentLifecycleCase lockByAppointment(Long appointmentOrderId);
    void closeUnpaid(PaymentLifecycleCase payment);
}

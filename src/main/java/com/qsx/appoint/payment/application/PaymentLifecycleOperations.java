package com.qsx.appoint.payment.application;

public interface PaymentLifecycleOperations {
    PaymentLifecycleView findPayment(Long paymentOrderId);
    PaymentLifecycleView lockByAppointment(Long appointmentOrderId);
    void markPaid(Long paymentOrderId);
    void closeUnpaid(Long paymentOrderId);
    void refundPaid(Long paymentOrderId);
}

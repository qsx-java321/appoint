package com.qsx.appoint.appointment.service;

import com.qsx.appoint.payment.dto.PaymentResponse;

public interface AppointmentLifecycleService {
    PaymentResponse simulatePaymentSuccess(Long userId, Long paymentId);
    void cancelOwned(Long userId, Long appointmentOrderId);
    boolean expireIfDue(Long appointmentOrderId);
}

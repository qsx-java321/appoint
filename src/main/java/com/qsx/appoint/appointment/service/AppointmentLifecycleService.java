package com.qsx.appoint.appointment.service;

import com.qsx.appoint.payment.dto.PaymentResponse;
import com.qsx.appoint.catalog.entity.Schedule;

import java.time.LocalDateTime;

public interface AppointmentLifecycleService {
    PaymentResponse simulatePaymentSuccess(Long userId, Long paymentId);
    void cancelOwned(Long userId, Long appointmentOrderId);
    boolean expireIfDue(Long appointmentOrderId);
    void closeScheduleLocked(Schedule schedule, Long operatorUserId, LocalDateTime now);
}

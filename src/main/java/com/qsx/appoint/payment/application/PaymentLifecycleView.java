package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
public record PaymentLifecycleView(
        Long id,
        String paymentNo,
        Long appointmentOrderId,
        Long userId,
        PaymentOrderStatus status,
        java.time.LocalDateTime paidAt) {
}

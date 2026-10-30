package com.qsx.appoint.payment.application;

public record PaymentLifecycleView(
        Long id,
        String paymentNo,
        Long appointmentOrderId,
        Long userId,
        String status,
        java.time.LocalDateTime paidAt) {
}

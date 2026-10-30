package com.qsx.appoint.payment.dto;

public record PaymentNotificationResponse(
        String notificationId,
        String processingResult,
        Long paymentOrderId,
        String paymentStatus,
        boolean duplicate,
        String eventId) {
}

package com.qsx.appoint.coordination.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** PaymentSucceeded v1 的稳定业务字段。 */
public record PaymentSucceededEvent(
        String eventId,
        int eventVersion,
        LocalDateTime occurredAt,
        Long paymentOrderId,
        Long appointmentOrderId,
        Long paymentAttemptId,
        BigDecimal amount,
        String currency,
        String channel,
        LocalDateTime paidAt) {
}

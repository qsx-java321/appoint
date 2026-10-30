package com.qsx.appoint.payment.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VerifiedPaymentNotification(
        String notificationId,
        String providerOrderNo,
        String providerTransactionNo,
        GatewayPaymentStatus status,
        BigDecimal amount,
        String currency,
        LocalDateTime occurredAt) {
}

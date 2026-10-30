package com.qsx.appoint.payment.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GatewayPaymentRequest(
        String paymentNo,
        String attemptNo,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt) {
}

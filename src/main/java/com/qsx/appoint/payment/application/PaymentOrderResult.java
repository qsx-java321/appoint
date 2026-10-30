package com.qsx.appoint.payment.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentOrderResult(
        Long paymentOrderId,
        String paymentNo,
        String status,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt) {
}


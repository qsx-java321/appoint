package com.qsx.appoint.payment.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreatePaymentOrderCommand(
        Long appointmentOrderId,
        Long userId,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {
}


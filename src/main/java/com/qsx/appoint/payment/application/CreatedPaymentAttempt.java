package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record CreatedPaymentAttempt(
        Long paymentOrderId,
        String paymentNo,
        Long attemptId,
        String attemptNo,
        PaymentChannel channel,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt) {
}

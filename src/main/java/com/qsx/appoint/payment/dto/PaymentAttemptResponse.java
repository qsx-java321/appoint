package com.qsx.appoint.payment.dto;

import com.qsx.appoint.payment.domain.PaymentChannel;

import java.time.LocalDateTime;

public record PaymentAttemptResponse(
        Long paymentOrderId,
        Long paymentAttemptId,
        PaymentChannel channel,
        String paymentStatus,
        String attemptStatus,
        String providerOrderNo,
        String checkoutUrl,
        LocalDateTime expiresAt) {
}

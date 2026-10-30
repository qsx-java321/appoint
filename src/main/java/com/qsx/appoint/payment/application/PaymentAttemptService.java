package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;

public interface PaymentAttemptService {
    PaymentAttemptResponse startAttempt(Long userId, Long paymentOrderId, PaymentChannel channel);
}

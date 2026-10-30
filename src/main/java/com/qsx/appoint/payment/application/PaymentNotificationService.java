package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.port.VerifiedPaymentNotification;

public interface PaymentNotificationService {
    PaymentNotificationResponse handle(PaymentChannel channel, VerifiedPaymentNotification notification,
                                       String payloadHash);
}

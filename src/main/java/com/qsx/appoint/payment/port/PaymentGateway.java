package com.qsx.appoint.payment.port;

import com.qsx.appoint.payment.domain.PaymentChannel;

public interface PaymentGateway {
    PaymentChannel channel();
    GatewayPaymentSession createPayment(GatewayPaymentRequest request);
    void closePayment(GatewayCloseRequest request);
    GatewayRefundSubmission refund(GatewayRefundRequest request);
    GatewayPaymentSnapshot queryPayment(String providerOrderNo);
    VerifiedPaymentNotification verifyNotification(RawPaymentNotification notification);
}

package com.qsx.appoint.payment.port;

public record GatewayPaymentSession(
        String providerOrderNo,
        GatewayPaymentStatus status,
        String checkoutUrl) {
}

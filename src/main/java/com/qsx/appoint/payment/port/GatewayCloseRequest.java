package com.qsx.appoint.payment.port;

public record GatewayCloseRequest(String paymentNo, String attemptNo, String providerOrderNo) {
}

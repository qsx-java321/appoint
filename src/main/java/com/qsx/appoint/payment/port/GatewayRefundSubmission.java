package com.qsx.appoint.payment.port;

public record GatewayRefundSubmission(String providerRefundNo, GatewayRefundStatus status) {
}

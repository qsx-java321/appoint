package com.qsx.appoint.payment.port;

import java.math.BigDecimal;

public record GatewayRefundRequest(
        String refundNo,
        String providerTransactionNo,
        BigDecimal amount,
        String currency,
        String reason) {
}

package com.qsx.appoint.payment.port;

import java.math.BigDecimal;

public record GatewayPaymentSnapshot(
        String providerOrderNo,
        String providerTransactionNo,
        GatewayPaymentStatus status,
        BigDecimal amount,
        String currency) {
}

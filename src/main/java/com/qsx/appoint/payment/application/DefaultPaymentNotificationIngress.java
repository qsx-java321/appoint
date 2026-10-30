package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.port.PaymentGateway;
import com.qsx.appoint.payment.port.PaymentGatewayRegistry;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import com.qsx.appoint.payment.port.VerifiedPaymentNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
class DefaultPaymentNotificationIngress implements PaymentNotificationIngress {
    private final PaymentGatewayRegistry gateways;
    private final PaymentNotificationService notificationService;

    @Override
    public PaymentNotificationResponse handle(PaymentChannel channel, RawPaymentNotification raw) {
        PaymentGateway gateway = gateways.require(channel);
        VerifiedPaymentNotification verified = gateway.verifyNotification(raw);
        return notificationService.handle(channel, verified, hash(verified));
    }

    private String hash(VerifiedPaymentNotification notification) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(notification.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }
}

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

/**
 * 支付通知的事务外适配：查找渠道、验签、生成已验签内容摘要，再委托事务服务。
 * <p>摘要用于同notificationId异内容冲突检测，不代替渠道签名验证。
 */
@Service
@RequiredArgsConstructor
class DefaultPaymentNotificationIngress implements PaymentNotificationIngress {
    private final PaymentGatewayRegistry gateways;
    private final PaymentNotificationService notificationService;
    /** {@inheritDoc} */
    @Override
    public PaymentNotificationResponse handle(PaymentChannel channel, RawPaymentNotification raw) {
        PaymentGateway gateway = gateways.require(channel);
        VerifiedPaymentNotification verified = gateway.verifyNotification(raw);
        return notificationService.handle(channel, verified, hash(verified));
    }

    /**
     * 对已验签字段生成SHA-256摘要，用于通知ID复用冲突检测。
     * <p>不包含原signature字段，验签由Gateway先完成；不能把摘要当渠道真实性证明。
     */
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

package com.qsx.appoint.payment.adapter.mock;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile({"dev", "test"})
public class MockPaymentGateway implements PaymentGateway {
    private final byte[] signingKey;
    private final Map<String, MockPayment> byAttemptNo = new ConcurrentHashMap<>();
    private final Map<String, MockPayment> byProviderOrderNo = new ConcurrentHashMap<>();
    private final Map<String, MockRefund> refunds = new ConcurrentHashMap<>();

    public MockPaymentGateway(@Value("${appoint.mock-pay.signing-key:local-test-signing-key}") String signingKey) {
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.MOCK;
    }

    @Override
    public synchronized GatewayPaymentSession createPayment(GatewayPaymentRequest request) {
        validatePaymentRequest(request);
        MockPayment existing = byAttemptNo.get(request.attemptNo());
        if (existing != null) {
            if (!existing.request().equals(request)) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return existing.session();
        }
        String providerOrderNo = "mock-pay-" + request.attemptNo();
        GatewayPaymentSession session = new GatewayPaymentSession(providerOrderNo,
                GatewayPaymentStatus.PROCESSING, "/api/v1/mock-pay/attempts/" + request.attemptNo());
        MockPayment payment = new MockPayment(request, session, null);
        byAttemptNo.put(request.attemptNo(), payment);
        byProviderOrderNo.put(providerOrderNo, payment);
        return session;
    }

    @Override
    public synchronized void closePayment(GatewayCloseRequest request) {
        MockPayment payment = requirePayment(request.providerOrderNo());
        if (!payment.request().paymentNo().equals(request.paymentNo())
                || !payment.request().attemptNo().equals(request.attemptNo())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        if (payment.session().status() == GatewayPaymentStatus.SUCCEEDED) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        replace(payment, GatewayPaymentStatus.CLOSED, payment.providerTransactionNo());
    }

    @Override
    public synchronized GatewayRefundSubmission refund(GatewayRefundRequest request) {
        validateRefundRequest(request);
        MockRefund existing = refunds.get(request.refundNo());
        if (existing != null) {
            if (!existing.request().equals(request)) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return existing.submission();
        }
        MockPayment payment = byProviderOrderNo.values().stream()
                .filter(candidate -> request.providerTransactionNo().equals(candidate.providerTransactionNo()))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (payment.session().status() != GatewayPaymentStatus.SUCCEEDED
                || payment.request().amount().compareTo(request.amount()) != 0
                || !payment.request().currency().equals(request.currency())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        GatewayRefundSubmission submission = new GatewayRefundSubmission(
                "mock-refund-" + request.refundNo(), GatewayRefundStatus.SUCCEEDED);
        refunds.put(request.refundNo(), new MockRefund(request, submission));
        return submission;
    }

    @Override
    public GatewayPaymentSnapshot queryPayment(String providerOrderNo) {
        MockPayment payment = requirePayment(providerOrderNo);
        return new GatewayPaymentSnapshot(providerOrderNo, payment.providerTransactionNo(),
                payment.session().status(), payment.request().amount(), payment.request().currency());
    }

    @Override
    public VerifiedPaymentNotification verifyNotification(RawPaymentNotification notification) {
        if (notification == null || notification.signature() == null
                || !MessageDigest.isEqual(sign(notification).getBytes(StandardCharsets.UTF_8),
                notification.signature().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.PAYMENT_SIGNATURE_INVALID);
        }
        return new VerifiedPaymentNotification(notification.notificationId(), notification.providerOrderNo(),
                notification.providerTransactionNo(), notification.status(), notification.amount(),
                notification.currency(), notification.occurredAt());
    }

    public synchronized RawPaymentNotification complete(String providerOrderNo, String notificationId,
                                                         LocalDateTime occurredAt) {
        MockPayment payment = requirePayment(providerOrderNo);
        if (payment.session().status() == GatewayPaymentStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        String transactionNo = payment.providerTransactionNo() == null
                ? "mock-txn-" + payment.request().attemptNo() : payment.providerTransactionNo();
        replace(payment, GatewayPaymentStatus.SUCCEEDED, transactionNo);
        return signedNotification(notificationId, providerOrderNo, transactionNo,
                GatewayPaymentStatus.SUCCEEDED, payment.request().amount(), payment.request().currency(), occurredAt);
    }

    public RawPaymentNotification completeAttempt(String attemptNo) {
        MockPayment payment = byAttemptNo.get(attemptNo);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return complete(payment.session().providerOrderNo(), "mock-notify-" + attemptNo, LocalDateTime.now());
    }

    public synchronized RawPaymentNotification fail(String providerOrderNo, String notificationId,
                                                     LocalDateTime occurredAt) {
        MockPayment payment = requirePayment(providerOrderNo);
        if (payment.session().status() == GatewayPaymentStatus.SUCCEEDED) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        replace(payment, GatewayPaymentStatus.FAILED, null);
        return signedNotification(notificationId, providerOrderNo, null,
                GatewayPaymentStatus.FAILED, payment.request().amount(), payment.request().currency(), occurredAt);
    }

    public RawPaymentNotification failAttempt(String attemptNo) {
        MockPayment payment = byAttemptNo.get(attemptNo);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return fail(payment.session().providerOrderNo(), "mock-notify-" + attemptNo, LocalDateTime.now());
    }

    public RawPaymentNotification signedNotification(String notificationId, String providerOrderNo,
                                                      String providerTransactionNo, GatewayPaymentStatus status,
                                                      BigDecimal amount, String currency, LocalDateTime occurredAt) {
        RawPaymentNotification unsigned = new RawPaymentNotification(notificationId, providerOrderNo,
                providerTransactionNo, status, amount, currency, occurredAt, "");
        return new RawPaymentNotification(notificationId, providerOrderNo, providerTransactionNo,
                status, amount, currency, occurredAt, sign(unsigned));
    }

    private void replace(MockPayment payment, GatewayPaymentStatus status, String transactionNo) {
        GatewayPaymentSession session = new GatewayPaymentSession(payment.session().providerOrderNo(), status,
                payment.session().checkoutUrl());
        MockPayment updated = new MockPayment(payment.request(), session, transactionNo);
        byAttemptNo.put(payment.request().attemptNo(), updated);
        byProviderOrderNo.put(session.providerOrderNo(), updated);
    }

    private MockPayment requirePayment(String providerOrderNo) {
        MockPayment payment = byProviderOrderNo.get(providerOrderNo);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return payment;
    }

    private void validatePaymentRequest(GatewayPaymentRequest request) {
        if (request == null || blank(request.paymentNo()) || blank(request.attemptNo())
                || request.amount() == null || request.amount().signum() <= 0
                || !"CNY".equals(request.currency()) || request.expiresAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateRefundRequest(GatewayRefundRequest request) {
        if (request == null || blank(request.refundNo()) || blank(request.providerTransactionNo())
                || request.amount() == null || request.amount().signum() <= 0 || !"CNY".equals(request.currency())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String sign(RawPaymentNotification notification) {
        String canonical = String.join("\n",
                value(notification.notificationId()), value(notification.providerOrderNo()),
                value(notification.providerTransactionNo()), value(notification.status()),
                notification.amount() == null ? "" : notification.amount().toPlainString(),
                value(notification.currency()), value(notification.occurredAt()));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 MockPay 签名", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private record MockPayment(GatewayPaymentRequest request, GatewayPaymentSession session,
                               String providerTransactionNo) {
    }

    private record MockRefund(GatewayRefundRequest request, GatewayRefundSubmission submission) {
    }
}

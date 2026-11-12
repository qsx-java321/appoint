package com.qsx.appoint.payment;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.port.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 直接构造真实Mock渠道及Registry，每例新建内存状态；Spring装配和profile由集成测试负责。 */
class PaymentGatewayContractTest {
    private PaymentGatewayRegistry registry;
    private MockPaymentGateway mockGateway;

    @org.junit.jupiter.api.BeforeEach
    void createFreshGateway() {
        mockGateway = new MockPaymentGateway("contract-test-key");
        registry = new PaymentGatewayRegistry(java.util.List.of(mockGateway));
    }

    @Test
    void registryResolvesMockAndRejectsUnavailableChannel() {
        assertThat(registry.require(PaymentChannel.MOCK)).isSameAs(mockGateway);
        assertThatThrownBy(() -> registry.require(PaymentChannel.WECHAT_PAY))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_CHANNEL_UNAVAILABLE));
    }

    @Test
    void repeatedCreateUsesAttemptNumberAsIdempotencyKey() {
        GatewayPaymentRequest request = request();
        GatewayPaymentSession first = mockGateway.createPayment(request);
        GatewayPaymentSession repeated = mockGateway.createPayment(request);

        assertThat(first).isEqualTo(repeated);
        assertThat(first.status()).isEqualTo(GatewayPaymentStatus.PROCESSING);
        assertThat(first.checkoutUrl()).contains(request.attemptNo());
    }

    @Test
    void processingSuccessFailureAndQueryMapToStableStatuses() {
        GatewayPaymentRequest successRequest = request();
        GatewayPaymentSession success = mockGateway.createPayment(successRequest);
        RawPaymentNotification successNotification = mockGateway.complete(success.providerOrderNo(),
                unique("notify"), LocalDateTime.of(2026, 10, 29, 12, 0));
        assertThat(mockGateway.queryPayment(success.providerOrderNo()).status())
                .isEqualTo(GatewayPaymentStatus.SUCCEEDED);
        assertThat(mockGateway.verifyNotification(successNotification).providerTransactionNo()).isNotBlank();

        GatewayPaymentRequest failedRequest = request();
        GatewayPaymentSession failed = mockGateway.createPayment(failedRequest);
        RawPaymentNotification failedNotification = mockGateway.fail(failed.providerOrderNo(),
                unique("notify"), LocalDateTime.of(2026, 10, 29, 12, 1));
        assertThat(mockGateway.queryPayment(failed.providerOrderNo()).status()).isEqualTo(GatewayPaymentStatus.FAILED);
        assertThat(mockGateway.verifyNotification(failedNotification).status()).isEqualTo(GatewayPaymentStatus.FAILED);
    }

    @Test
    void forgedNotificationIsRejectedBeforeItCanBeTrusted() {
        GatewayPaymentSession session = mockGateway.createPayment(request());
        RawPaymentNotification signed = mockGateway.complete(session.providerOrderNo(), unique("notify"), LocalDateTime.now());
        RawPaymentNotification forged = new RawPaymentNotification(signed.notificationId(), signed.providerOrderNo(),
                signed.providerTransactionNo(), signed.status(), signed.amount(), signed.currency(),
                signed.occurredAt(), signed.signature() + "00");

        assertThatThrownBy(() -> mockGateway.verifyNotification(forged))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_SIGNATURE_INVALID));
    }

    @Test
    void duplicateNotificationVerificationIsPureAndStable() {
        GatewayPaymentSession session = mockGateway.createPayment(request());
        RawPaymentNotification notification = mockGateway.complete(session.providerOrderNo(), unique("notify"), LocalDateTime.now());
        assertThat(mockGateway.verifyNotification(notification)).isEqualTo(mockGateway.verifyNotification(notification));
    }

    @Test
    void closeIsIdempotentAndSuccessfulPaymentCannotBeClosed() {
        GatewayPaymentRequest closeRequest = request();
        GatewayPaymentSession closeSession = mockGateway.createPayment(closeRequest);
        GatewayCloseRequest close = new GatewayCloseRequest(closeRequest.paymentNo(), closeRequest.attemptNo(),
                closeSession.providerOrderNo());
        mockGateway.closePayment(close);
        mockGateway.closePayment(close);
        assertThat(mockGateway.queryPayment(closeSession.providerOrderNo()).status()).isEqualTo(GatewayPaymentStatus.CLOSED);

        GatewayPaymentRequest paidRequest = request();
        GatewayPaymentSession paid = mockGateway.createPayment(paidRequest);
        mockGateway.complete(paid.providerOrderNo(), unique("notify"), LocalDateTime.now());
        assertThatThrownBy(() -> mockGateway.closePayment(new GatewayCloseRequest(
                paidRequest.paymentNo(), paidRequest.attemptNo(), paid.providerOrderNo())))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_ERROR));
    }

    @Test
    void fullRefundUsesRefundNumberIdempotently() {
        GatewayPaymentRequest paymentRequest = request();
        GatewayPaymentSession session = mockGateway.createPayment(paymentRequest);
        String transactionNo = mockGateway.complete(session.providerOrderNo(), unique("notify"), LocalDateTime.now())
                .providerTransactionNo();
        GatewayRefundRequest refund = new GatewayRefundRequest(unique("refund"), transactionNo,
                paymentRequest.amount(), paymentRequest.currency(), "预约取消");

        GatewayRefundSubmission first = mockGateway.refund(refund);
        GatewayRefundSubmission repeated = mockGateway.refund(refund);
        assertThat(first).isEqualTo(repeated);
        assertThat(first.status()).isEqualTo(GatewayRefundStatus.SUCCEEDED);
    }

    private GatewayPaymentRequest request() {
        return new GatewayPaymentRequest(unique("payment"), unique("attempt"), new BigDecimal("50.00"),
                "CNY", LocalDateTime.of(2099, 1, 1, 8, 15));
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}

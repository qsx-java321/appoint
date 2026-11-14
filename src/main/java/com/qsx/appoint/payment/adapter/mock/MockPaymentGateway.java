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

/**
 * 仅dev/test的内存模拟支付渠道，实现下单、查询、验签和按编号幂等退款。
 * <p>以attemptNo/refundNo判断重复，同号异内容拒绝；synchronized串行化复合状态变更。
 * 内存渠道事实随进程重启丢失，不能当作生产支付存储或持久化恢复保证。
 */
@Component
@Profile({"dev", "test", "demo", "regression"})
public class MockPaymentGateway implements PaymentGateway {
    /** 仅模拟渠道使用的HMAC密钥字节，禁止写入日志、响应或审计记录。 */
    private final byte[] signingKey;
    private final Map<String, MockPayment> byAttemptNo = new ConcurrentHashMap<>();
    private final Map<String, MockPayment> byProviderOrderNo = new ConcurrentHashMap<>();
    private final Map<String, MockRefund> refunds = new ConcurrentHashMap<>();

    public MockPaymentGateway(@Value("${appoint.mock-pay.signing-key:local-test-signing-key}") String signingKey) {
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }
    /** {@inheritDoc} */
    @Override
    public PaymentChannel channel() {
        return PaymentChannel.MOCK;
    }
    /** {@inheritDoc} */
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
    /** {@inheritDoc} */
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
    /** {@inheritDoc} */
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
    /** {@inheritDoc} */
    @Override
    public GatewayPaymentSnapshot queryPayment(String providerOrderNo) {
        MockPayment payment = requirePayment(providerOrderNo);
        return new GatewayPaymentSnapshot(providerOrderNo, payment.providerTransactionNo(),
                payment.session().status(), payment.request().amount(), payment.request().currency());
    }
    /** {@inheritDoc} */
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

    /**
     * 在模拟渠道内记录成功并生成签名通知，不直接写本地支付表。
     * @param providerOrderNo 渠道订单号
     * @param notificationId 指定通知幂等ID，测试可据此模拟重复/冲突
     * @param occurredAt 模拟渠道时间，不替代通知事务的paidAt
     * @return 成功签名通知，重复成功沿用原交易流水
     */
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

    /**
     * 根据本地attemptNo定位模拟会话并产生固定通知ID的成功通知。
     * @return 签名通知；未知尝试报RESOURCE_NOT_FOUND，渠道已关闭则拒绝
     */
    public RawPaymentNotification completeAttempt(String attemptNo) {
        MockPayment payment = byAttemptNo.get(attemptNo);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return complete(payment.session().providerOrderNo(), "mock-notify-" + attemptNo, LocalDateTime.now());
    }

    /**
     * 模拟渠道失败；已SUCCEEDED不能被失败覆盖。
     * @param providerOrderNo 渠道订单号
     * @param notificationId 测试/模拟指定的通知ID
     * @param occurredAt 模拟渠道时间
     * @return 失败签名通知，不包含成功交易流水
     */
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

    /**
     * 按attemptNo定位模拟会话，产生固定通知ID的失败通知。
     * @return 失败签名报文，交Dispatcher/Ingress继续处理
     */
    public RawPaymentNotification failAttempt(String attemptNo) {
        MockPayment payment = byAttemptNo.get(attemptNo);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return fail(payment.session().providerOrderNo(), "mock-notify-" + attemptNo, LocalDateTime.now());
    }

    /**
     * 根据给定字段生成HMAC签名原始通知，用于模拟渠道和验签测试。
     * <p>仅构造报文，不保证对应本地业务存在或金额合法，业务层仍须校验。
     * @return 带签名通知；调用者不应把签名密钥或完整敏感报文写日志
     */
    public RawPaymentNotification signedNotification(String notificationId, String providerOrderNo,
                                                      String providerTransactionNo, GatewayPaymentStatus status,
                                                      BigDecimal amount, String currency, LocalDateTime occurredAt) {
        RawPaymentNotification unsigned = new RawPaymentNotification(notificationId, providerOrderNo,
                providerTransactionNo, status, amount, currency, occurredAt, "");
                /**
                 * 按固定字段顺序和换行规范化报文，使用HmacSHA256生成十六进制签名。
                 * <p>金额使用toPlainString，签名与验签必须采用同一文本规则；不能记录密钥。
                 */
                return new RawPaymentNotification(notificationId, providerOrderNo, providerTransactionNo,
                status, amount, currency, occurredAt, sign(unsigned));
    }

    /**
     * 以新不可变模拟会话同时更新attemptNo和providerOrderNo两个索引。
     * <p>调用方复合状态变更持有实例monitor，避免两份索引指向不同状态。
     */
    private void replace(MockPayment payment, GatewayPaymentStatus status, String transactionNo) {
        GatewayPaymentSession session = new GatewayPaymentSession(payment.session().providerOrderNo(), status,
                payment.session().checkoutUrl());
        MockPayment updated = new MockPayment(payment.request(), session, transactionNo);
        byAttemptNo.put(payment.request().attemptNo(), updated);
        byProviderOrderNo.put(session.providerOrderNo(), updated);
    }
    /** 按渠道订单号查找内存会话，未知订单抛RESOURCE_NOT_FOUND，不创建占位订单。 */
    private MockPayment requirePayment(String providerOrderNo) {
        MockPayment payment = byProviderOrderNo.get(providerOrderNo);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return payment;
    }
    /** 验证模拟下单标识、正金额、CNY及截止非空；不是预约资格或截止前支付判断。 */
    private void validatePaymentRequest(GatewayPaymentRequest request) {
        if (request == null || blank(request.paymentNo()) || blank(request.attemptNo())
                || request.amount() == null || request.amount().signum() <= 0
                || !"CNY".equals(request.currency()) || request.expiresAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
    /** 验证模拟退款标识、原交易流水、正金额和CNY；原交易及全额匹配在refund继续检查。 */
    private void validateRefundRequest(GatewayRefundRequest request) {
        if (request == null || blank(request.refundNo()) || blank(request.providerTransactionNo())
                || request.amount() == null || request.amount().signum() <= 0 || !"CNY".equals(request.currency())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
    /** 将null或仅空白的渠道标识视为缺失。 */
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
    /** 签名规范化时把null转为空字符串，其他值使用原文本表示。 */
    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private record MockPayment(GatewayPaymentRequest request, GatewayPaymentSession session,
                               String providerTransactionNo) {
    }

    private record MockRefund(GatewayRefundRequest request, GatewayRefundSubmission submission) {
    }
}

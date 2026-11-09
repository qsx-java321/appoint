package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.PaymentAttemptStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.entity.PaymentNotification;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentNotificationMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.port.VerifiedPaymentNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付通知核心事务：快速幂等检查→父子锁上下文→锁下幂等复验→Policy→原子执行。
 * <p>Policy只决定动作；本类统一执行资金、审计、Outbox和迟到退款，失败整体回滚。
 * paidAt取本系统锁后确认资金的时间，不能使用渠道声明的occurredAt代替。
 * @see PaymentNotificationPolicy
 */
@Service
@RequiredArgsConstructor
class PaymentNotificationServiceImpl implements PaymentNotificationService {
    private final PaymentNotificationPolicy policy = new PaymentNotificationPolicy();
    private final PaymentNotificationMapper notificationMapper;
    private final PaymentAttemptMapper attemptMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOutboxEventMapper outboxMapper;
    private final PaymentRefundOperations refunds;
    private final ObjectMapper objectMapper;
    private final Clock appointmentClock;
    /** {@inheritDoc} */
    @Override
    @Transactional
    public PaymentNotificationResponse handle(PaymentChannel channel, VerifiedPaymentNotification verified,
                                              String payloadHash) {
        PaymentNotification existing = notificationMapper.selectByChannelAndNotificationId(
                channel.name(), verified.notificationId());
        if (existing != null) {
            if (!existing.getPayloadHash().equals(payloadHash)) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return new PaymentNotificationResponse(verified.notificationId(), existing.getProcessingResult(),
                    null, null, true, null);
        }

        // 先完成普通幂等短路，再建立payment父锁保护的上下文；未知渠道订单只做拒绝审计。
        NotificationContext context = lockContext(channel, verified);
        if (context == null) return execute(channel, verified, payloadHash, null, policy.decide(verified, null));
        PaymentOrder payment = context.payment();
        PaymentAttempt attempt = context.attempt();
        // 等待父锁期间另一个同通知请求可能已提交，必须在锁下再次检查摘要与幂等结果。
        existing = notificationMapper.selectByChannelAndNotificationIdForUpdate(
                channel.name(), verified.notificationId());
        if (existing != null) {
            if (!existing.getPayloadHash().equals(payloadHash)) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return new PaymentNotificationResponse(verified.notificationId(), existing.getProcessingResult(),
                    attempt.getPaymentOrderId(), null, true, null);
        }
        // Policy只读取锁后事实，实际迁移和审计仍在同一handle事务里执行。
        var decision = policy.decide(verified, new PaymentNotificationPolicy.Facts(
                payment.getAmount(), payment.getCurrency(), PaymentAttemptStatus.fromStored(attempt.getStatus()),
                PaymentOrderStatus.fromStored(payment.getStatus())));
        return execute(channel, verified, payloadHash, context, decision);
    }

    /**
     * 只读定位attempt，再锁payment父记录及attempt，并锁后复验ID和关联。
     * @return 私有锁上下文；找不到渠道订单返回null供拒绝审计，支付关联缺失不作未知订单处理
     */
    private NotificationContext lockContext(PaymentChannel channel, VerifiedPaymentNotification verified) {
        PaymentAttempt lookup = attemptMapper.selectByProviderOrderNo(channel.name(), verified.providerOrderNo());
        if (lookup == null) return null;
        PaymentOrder payment = paymentOrderMapper.selectByIdForUpdate(lookup.getPaymentOrderId());
        if (payment == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        PaymentAttempt attempt = attemptMapper.selectByProviderOrderNoForUpdate(channel.name(), verified.providerOrderNo());
        if (attempt == null || !attempt.getId().equals(lookup.getId())
                || !attempt.getPaymentOrderId().equals(payment.getId())) {
            throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
        }
        return new NotificationContext(payment, attempt);
    }

    /**
     * 按纯决策在当前事务执行迁移和审计，所有资金成功写入都同时产生成功Outbox。
     * <p>未知订单/金额拒绝不采样资金成功时间；成功paidAt在此锁后获取。
     * @param context 已锁的支付/尝试；仅未知订单拒绝允许null
     * @param decision 通过幂等和完整性检查后得到的动作
     * @return 处理摘要，拒绝也记录审计，迟到支付额外确保退款
     */
    private PaymentNotificationResponse execute(PaymentChannel channel, VerifiedPaymentNotification verified,
                                                String payloadHash, NotificationContext context,
                                                PaymentNotificationPolicy.Decision decision) {
        PaymentOrder payment = context == null ? null : context.payment();
        PaymentAttempt attempt = context == null ? null : context.attempt();
        // 拒绝未知订单/金额时沿用原审计取时，不读取资金成功时间。
        LocalDateTime now = decision == PaymentNotificationPolicy.Decision.REJECT_UNKNOWN_ORDER
                || decision == PaymentNotificationPolicy.Decision.REJECT_AMOUNT_OR_CURRENCY ? null : now();
        return switch (decision) {
            case ALREADY_SUCCEEDED -> audit(channel, verified, payloadHash, "ALREADY_SUCCEEDED",
                    payment.getId(), payment.getStatus(), false);
            case IGNORE_LATE_FAILURE -> audit(channel, verified, payloadHash, "IGNORED_LATE_FAILURE",
                    payment.getId(), payment.getStatus(), false);
            case REJECT_UNKNOWN_ORDER -> auditRejected(channel, verified, payloadHash, "REJECTED_UNKNOWN_ORDER");
            case REJECT_AMOUNT_OR_CURRENCY -> audit(channel, verified, payloadHash, "REJECTED_AMOUNT_OR_CURRENCY",
                    payment.getId(), payment.getStatus(), false);
            case REJECT_STATE -> audit(channel, verified, payloadHash, "REJECTED_STATE",
                    payment.getId(), payment.getStatus(), false);
            case FAIL -> {
                requireOne(attemptMapper.markFailedByNotification(attempt.getId(), now));
                requireOne(paymentOrderMapper.restoreUnpaidIfPaying(payment.getId()));
                /** 记录不带新事件的通知审计，委托完整审计方法；不另开事务。 */
                yield audit(channel, verified, payloadHash, "FAILED", payment.getId(), "UNPAID", false);
            }
            case SUCCEED, LATE_PAYMENT -> {
                boolean latePayment = decision == PaymentNotificationPolicy.Decision.LATE_PAYMENT;
                requireOne(attemptMapper.markSucceeded(attempt.getId(), verified.providerTransactionNo(), now));
                requireOne(latePayment
                        ? paymentOrderMapper.recordLatePaymentIfClosed(payment.getId(), now)
                        : paymentOrderMapper.payIfPaying(payment.getId(), now));
                String eventId = insertOutbox(payment, attempt, verified, now);
                if (latePayment) {
                    refunds.requestFullRefund(payment.getId(), RefundReason.LATE_PAYMENT);
                    /**
                     * 保存已验签通知的幂等审计，并返回本次处理摘要。
                     * <p>唯一键冲突转可重试错误，由当前事务回滚资金与事件，重试后读取既有审计。
                     * @param eventId 本次新建成功事件ID，无新事件时为null
                     * @return 含processingResult的摘要；审计成功不等于一定收款成功
                     */
                    yield audit(channel, verified, payloadHash, "SUCCEEDED_REFUND_PENDING",
                            payment.getId(), "REFUND_PENDING", false, eventId);
                }
                yield audit(channel, verified, payloadHash, "SUCCEEDED", payment.getId(), "PAID", false, eventId);
            }
        };
    }

    private record NotificationContext(PaymentOrder payment, PaymentAttempt attempt) { }
    /** 记录无法定位支付单的拒绝通知；返回结果不携带支付ID或资金状态。 */
    private PaymentNotificationResponse auditRejected(PaymentChannel channel, VerifiedPaymentNotification verified,
                                                      String hash, String result) {
        return audit(channel, verified, hash, result, null, null, false);
    }

    private PaymentNotificationResponse audit(PaymentChannel channel, VerifiedPaymentNotification verified,
                                               String hash, String result, Long paymentId,
                                               String paymentStatus, boolean duplicate) {
        return audit(channel, verified, hash, result, paymentId, paymentStatus, duplicate, null);
    }

    private PaymentNotificationResponse audit(PaymentChannel channel, VerifiedPaymentNotification verified,
                                               String hash, String result, Long paymentId,
                                               String paymentStatus, boolean duplicate, String eventId) {
        PaymentNotification audit = new PaymentNotification();
        audit.setChannel(channel.name());
        audit.setNotificationId(verified.notificationId());
        audit.setProviderTransactionNo(verified.providerTransactionNo());
        audit.setPayloadHash(hash);
        audit.setVerificationResult("VERIFIED");
        audit.setProcessingResult(result);
        audit.setReceivedAt(now());
        audit.setProcessedAt(now());
        try {
            notificationMapper.insert(audit);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
        }
        return new PaymentNotificationResponse(
                verified.notificationId(), result, paymentId, paymentStatus, duplicate, eventId);
    }

    /**
     * 创建PaymentSucceeded v1 Outbox；只插数据库，不在回调事务内等待Broker。
     * @return 新事件UUID，重发时必须沿用该ID
     */
    private String insertOutbox(PaymentOrder payment, PaymentAttempt attempt,
                                VerifiedPaymentNotification verified, LocalDateTime now) {
        PaymentOutboxEvent event = new PaymentOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType("PaymentOrder");
        event.setAggregateId(payment.getId());
        event.setEventType("PaymentSucceeded");
        event.setEventVersion(1);
        event.setPayload(payload(event.getEventId(), payment, attempt, verified, now));
        event.setOccurredAt(now);
        event.setStatus("PENDING");
        event.setPublishAttempts(0);
        event.setNextAttemptAt(now);
        outboxMapper.insert(event);
        return event.getEventId();
    }

    /**
     * 构造既有v1 envelope及payload，aggregateId始终为paymentOrderId。
     * @param occurredAt 本系统确认收款时间，同时写作paidAt；不是渠道上报时间
     * @return JSON正文，序列化失败令资金事务回滚
     */
    private String payload(String eventId, PaymentOrder payment, PaymentAttempt attempt,
                           VerifiedPaymentNotification verified, LocalDateTime occurredAt) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", "PaymentSucceeded");
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", payment.getId());
        envelope.put("occurredAt", occurredAt);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentOrderId", payment.getId());
        payload.put("appointmentOrderId", payment.getAppointmentOrderId());
        payload.put("paymentAttemptId", attempt.getId());
        payload.put("providerTransactionNo", verified.providerTransactionNo());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("channel", attempt.getChannel());
        payload.put("paidAt", occurredAt);
        envelope.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
    /** 资金或尝试迁移必须恰好命中一行，否则触发可重试并发错误并回滚审计和Outbox。 */
    private void requireOne(int updated) {
        if (updated != 1) throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
    }
    /** 读取截断到秒的服务端时间；资金确认与通知审计分别按其实际执行点采样。 */
    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

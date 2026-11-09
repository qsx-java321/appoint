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

        PaymentAttempt lookup = attemptMapper.selectByProviderOrderNo(channel.name(), verified.providerOrderNo());
        if (lookup == null) return auditRejected(channel, verified, payloadHash, "REJECTED_UNKNOWN_ORDER");
        PaymentOrder payment = paymentOrderMapper.selectByIdForUpdate(lookup.getPaymentOrderId());
        if (payment == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        PaymentAttempt attempt = attemptMapper.selectByProviderOrderNoForUpdate(channel.name(), verified.providerOrderNo());
        if (attempt == null || !attempt.getId().equals(lookup.getId())
                || !attempt.getPaymentOrderId().equals(payment.getId())) {
            throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
        }
        existing = notificationMapper.selectByChannelAndNotificationIdForUpdate(
                channel.name(), verified.notificationId());
        if (existing != null) {
            if (!existing.getPayloadHash().equals(payloadHash)) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return new PaymentNotificationResponse(verified.notificationId(), existing.getProcessingResult(),
                    attempt.getPaymentOrderId(), null, true, null);
        }
        if (payment.getAmount().compareTo(verified.amount()) != 0
                || !payment.getCurrency().equals(verified.currency())) {
            return audit(channel, verified, payloadHash, "REJECTED_AMOUNT_OR_CURRENCY",
                    payment.getId(), payment.getStatus(), false);
        }

        LocalDateTime now = now();
        var decision = policy.decide(
                verified.status(), PaymentAttemptStatus.fromStored(attempt.getStatus()), PaymentOrderStatus.fromStored(payment.getStatus()));
        return switch (decision) {
            case ALREADY_SUCCEEDED -> audit(channel, verified, payloadHash, "ALREADY_SUCCEEDED",
                    payment.getId(), payment.getStatus(), false);
            case IGNORE_LATE_FAILURE -> audit(channel, verified, payloadHash, "IGNORED_LATE_FAILURE",
                    payment.getId(), payment.getStatus(), false);
            case REJECT_STATE -> audit(channel, verified, payloadHash, "REJECTED_STATE",
                    payment.getId(), payment.getStatus(), false);
            case FAIL -> {
                requireOne(attemptMapper.markFailedByNotification(attempt.getId(), now));
                requireOne(paymentOrderMapper.restoreUnpaidIfPaying(payment.getId()));
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
                    yield audit(channel, verified, payloadHash, "SUCCEEDED_REFUND_PENDING",
                            payment.getId(), "REFUND_PENDING", false, eventId);
                }
                yield audit(channel, verified, payloadHash, "SUCCEEDED", payment.getId(), "PAID", false, eventId);
            }
        };
    }

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

    private void requireOne(int updated) {
        if (updated != 1) throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

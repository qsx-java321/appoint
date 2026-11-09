package com.qsx.appoint.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.entity.PaymentRefund;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import com.qsx.appoint.payment.mapper.PaymentRefundMapper;
import com.qsx.appoint.payment.port.GatewayRefundRequest;
import com.qsx.appoint.payment.port.GatewayRefundStatus;
import com.qsx.appoint.payment.port.GatewayRefundSubmission;
import com.qsx.appoint.payment.port.PaymentGatewayRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultRefundWorker implements RefundWorker {
    private final PaymentRefundMapper refundMapper;
    private final PaymentAttemptMapper attemptMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOutboxEventMapper outboxMapper;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final ObjectMapper objectMapper;
    private final Clock appointmentClock;
    private final TransactionTemplate transactionTemplate;

    @Override
    public String process(RefundRequestedEvent event) {
        validate(event);
        RefundWork work = transactionTemplate.execute(status -> claim(event));
        if (work == null) return "ALREADY_SUCCEEDED";
        try {
            GatewayRefundSubmission submission = gatewayRegistry.require(PaymentChannel.valueOf(work.channel()))
                    .refund(new GatewayRefundRequest(work.refundNo(), work.providerTransactionNo(),
                            work.amount(), work.currency(), work.reason()));
            if (submission.status() != GatewayRefundStatus.SUCCEEDED) {
                throw new IllegalStateException("渠道未确认退款成功: " + submission.status());
            }
            transactionTemplate.executeWithoutResult(status -> complete(work, submission.providerRefundNo()));
            return "SUCCEEDED";
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> fail(work, exception));
            throw exception;
        }
    }

    private RefundWork claim(RefundRequestedEvent event) {
        PaymentRefund lookup = refundMapper.selectById(event.refundId());
        if (lookup == null || !lookup.getRefundNo().equals(event.refundNo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        lockPayment(lookup.getPaymentOrderId());
        PaymentRefund refund = refundMapper.selectByIdForUpdate(event.refundId());
        if (refund == null || !refund.getRefundNo().equals(event.refundNo())
                || !refund.getPaymentOrderId().equals(lookup.getPaymentOrderId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if ("SUCCEEDED".equals(refund.getStatus())) return null;
        if (refundMapper.beginAttempt(refund.getId(), now()) != 1) {
            throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
        }
        PaymentAttempt attempt = attemptMapper.selectByIdForUpdate(refund.getPaymentAttemptId());
        if (attempt == null || !attempt.getPaymentOrderId().equals(refund.getPaymentOrderId())
                || !"SUCCEEDED".equals(attempt.getStatus()) || attempt.getProviderTransactionNo() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new RefundWork(refund.getId(), refund.getRefundNo(), refund.getPaymentOrderId(),
                refund.getAmount(), refund.getCurrency(), refund.getChannel(), refund.getReason(),
                attempt.getProviderTransactionNo());
    }

    private void complete(RefundWork work, String providerRefundNo) {
        LocalDateTime now = now();
        lockPayment(work.paymentOrderId());
        PaymentRefund current = refundMapper.selectByIdForUpdate(work.refundId());
        if (current == null || !current.getPaymentOrderId().equals(work.paymentOrderId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if ("SUCCEEDED".equals(current.getStatus())) return;
        requireOne(refundMapper.markSucceeded(work.refundId(), providerRefundNo, now));
        requireOne(paymentOrderMapper.completeRefund(work.paymentOrderId(), now));
        insertResultEvent("RefundSucceeded", work, providerRefundNo, null, now);
    }

    private void fail(RefundWork work, RuntimeException exception) {
        LocalDateTime now = now();
        lockPayment(work.paymentOrderId());
        PaymentRefund current = refundMapper.selectByIdForUpdate(work.refundId());
        if (current == null) return;
        if (!current.getPaymentOrderId().equals(work.paymentOrderId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if ("SUCCEEDED".equals(current.getStatus())) return;
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (message.length() > 255) message = message.substring(0, 255);
        requireOne(refundMapper.markFailed(work.refundId(), "GATEWAY_ERROR", message, now));
        insertResultEvent("RefundFailed", work, null, message, now);
    }

    private void insertResultEvent(String type, RefundWork work, String providerRefundNo,
                                   String failureMessage, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", type);
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", work.paymentOrderId());
        envelope.put("occurredAt", now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", work.refundId());
        payload.put("refundNo", work.refundNo());
        payload.put("paymentOrderId", work.paymentOrderId());
        payload.put("providerRefundNo", providerRefundNo);
        payload.put("failureMessage", failureMessage);
        envelope.put("payload", payload);
        PaymentOutboxEvent outbox = new PaymentOutboxEvent();
        outbox.setEventId(eventId);
        outbox.setAggregateType("PaymentOrder");
        outbox.setAggregateId(work.paymentOrderId());
        outbox.setEventType(type);
        outbox.setEventVersion(1);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        outbox.setOccurredAt(now);
        outbox.setStatus("PENDING");
        outbox.setPublishAttempts(0);
        outbox.setNextAttemptAt(now);
        outboxMapper.insert(outbox);
    }

    private void validate(RefundRequestedEvent event) {
        try {
            if (event == null || event.eventVersion() != 1 || event.eventId() == null
                    || event.refundId() == null || event.refundNo() == null || event.refundNo().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            UUID.fromString(event.eventId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void lockPayment(Long paymentOrderId) {
        if (paymentOrderMapper.selectByIdForUpdate(paymentOrderId) == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void requireOne(int count) {
        if (count != 1) throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private record RefundWork(Long refundId, String refundNo, Long paymentOrderId,
                              java.math.BigDecimal amount, String currency, String channel,
                              String reason, String providerTransactionNo) {
    }
}

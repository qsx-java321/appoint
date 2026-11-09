package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.entity.PaymentRefund;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import com.qsx.appoint.payment.mapper.PaymentRefundMapper;
import lombok.RequiredArgsConstructor;
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
class DefaultPaymentRefundOperations implements PaymentRefundOperations {
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentAttemptMapper paymentAttemptMapper;
    private final PaymentRefundMapper refundMapper;
    private final PaymentOutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public void requestFullRefund(Long paymentOrderId, RefundReason reason) {
        PaymentOrder payment = paymentOrderMapper.selectByIdForUpdate(paymentOrderId);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        PaymentRefund existing = refundMapper.selectByPaymentOrderIdForUpdate(paymentOrderId);
        if (existing != null) return;
        if (PaymentOrderStatus.fromStored(payment.getStatus()) != PaymentOrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        PaymentAttempt attempt = paymentAttemptMapper.selectSucceededByPaymentOrderIdForUpdate(paymentOrderId);
        if (attempt == null || attempt.getProviderTransactionNo() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        LocalDateTime now = now();
        if (paymentOrderMapper.requestRefundIfPaid(paymentOrderId) != 1) {
            throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
        }
        PaymentRefund refund = new PaymentRefund();
        refund.setRefundNo("REF" + UUID.randomUUID().toString().replace("-", ""));
        refund.setPaymentOrderId(paymentOrderId);
        refund.setPaymentAttemptId(attempt.getId());
        refund.setAmount(payment.getAmount());
        refund.setCurrency(payment.getCurrency());
        refund.setChannel(attempt.getChannel());
        refund.setStatus("PENDING");
        refund.setReason(reason.name());
        refund.setAttemptCount(0);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        refundMapper.insert(refund);
        insertRequestedEvent(refund, attempt, now);
    }

    private void insertRequestedEvent(PaymentRefund refund, PaymentAttempt attempt, LocalDateTime now) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", "RefundRequested");
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", refund.getPaymentOrderId());
        envelope.put("occurredAt", now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("refundId", refund.getId());
        payload.put("refundNo", refund.getRefundNo());
        payload.put("paymentOrderId", refund.getPaymentOrderId());
        payload.put("paymentAttemptId", refund.getPaymentAttemptId());
        payload.put("providerTransactionNo", attempt.getProviderTransactionNo());
        payload.put("amount", refund.getAmount());
        payload.put("currency", refund.getCurrency());
        payload.put("channel", refund.getChannel());
        payload.put("reason", refund.getReason());
        envelope.put("payload", payload);

        PaymentOutboxEvent event = new PaymentOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("PaymentOrder");
        event.setAggregateId(refund.getPaymentOrderId());
        event.setEventType("RefundRequested");
        event.setEventVersion(1);
        try {
            event.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        event.setOccurredAt(now);
        event.setStatus("PENDING");
        event.setPublishAttempts(0);
        event.setNextAttemptAt(now);
        outboxMapper.insert(event);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

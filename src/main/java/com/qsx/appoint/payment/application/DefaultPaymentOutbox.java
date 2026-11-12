package com.qsx.appoint.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.contract.event.*;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

/** 把明确业务事实序列化并登记为待发送事件；不访问渠道或 Broker，不另开事务。 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class DefaultPaymentOutbox implements PaymentOutbox {
    private final ObjectMapper objectMapper;
    private final PaymentOutboxEventMapper mapper;
    @Override
    public String recordPaymentSucceeded(PaymentSucceededPayload payload, LocalDateTime occurredAt) {
        return append("PaymentSucceeded", payload.paymentOrderId(), occurredAt, payload);
    }
    @Override
    public String recordRefundRequested(RefundRequestedPayload payload, LocalDateTime occurredAt) {
        return append("RefundRequested", payload.paymentOrderId(), occurredAt, payload);
    }
    @Override
    public String recordRefundSucceeded(RefundSucceededPayload payload, LocalDateTime occurredAt) {
        return append("RefundSucceeded", payload.paymentOrderId(), occurredAt, payload);
    }
    @Override
    public String recordRefundFailed(RefundFailedPayload payload, LocalDateTime occurredAt) {
        return append("RefundFailed", payload.paymentOrderId(), occurredAt, payload);
    }

    /** 只在业务事务内创建一次；发布器以后转发这里保存的原正文和原ID。 */
    private <T> String append(String type, Long paymentId, LocalDateTime occurredAt, T payload) {
        String eventId = UUID.randomUUID().toString();
        PaymentOutboxEvent event = new PaymentOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("PaymentOrder");
        event.setAggregateId(paymentId);
        event.setEventType(type);
        event.setEventVersion(1);
        try {
            event.setPayload(objectMapper.writeValueAsString(new PaymentEventEnvelope<>(
                    eventId, type, 1, paymentId, occurredAt, payload)));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        event.setOccurredAt(occurredAt);
        event.setStatus("PENDING");
        event.setPublishAttempts(0);
        event.setNextAttemptAt(occurredAt);
        mapper.insert(event);
        return eventId;
    }
}

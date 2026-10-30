package com.qsx.appoint.coordination.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.application.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** PaymentSucceeded v1 的版本化 JSON 边界。 */
@Component
@RequiredArgsConstructor
public class PaymentSucceededEventCodec {
    private final ObjectMapper objectMapper;

    public PaymentSucceededEvent read(byte[] body) {
        try {
            JsonNode envelope = objectMapper.readTree(body);
            if (!"PaymentSucceeded".equals(text(envelope, "eventType"))
                    || integer(envelope, "eventVersion") != 1) {
                throw invalid();
            }
            JsonNode payload = envelope.required("payload");
            Long aggregateId = number(envelope, "aggregateId");
            Long paymentOrderId = number(payload, "paymentOrderId");
            if (!aggregateId.equals(paymentOrderId)) throw invalid();
            return new PaymentSucceededEvent(
                    text(envelope, "eventId"), 1,
                    time(envelope, "occurredAt"), paymentOrderId,
                    number(payload, "appointmentOrderId"),
                    number(payload, "paymentAttemptId"),
                    decimal(payload, "amount"), text(payload, "currency"),
                    text(payload, "channel"), time(payload, "paidAt"));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.required(field).textValue();
        if (value == null || value.isBlank()) throw invalid();
        return value;
    }

    private int integer(JsonNode node, String field) {
        if (!node.required(field).canConvertToInt()) throw invalid();
        return node.required(field).intValue();
    }

    private Long number(JsonNode node, String field) {
        JsonNode value = node.required(field);
        if (value.canConvertToLong()) return value.longValue();
        if (value.isTextual()) {
            try {
                return Long.valueOf(value.textValue());
            } catch (NumberFormatException exception) {
                throw invalid();
            }
        }
        throw invalid();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (!node.required(field).isNumber()) throw invalid();
        return node.required(field).decimalValue();
    }

    private LocalDateTime time(JsonNode node, String field) {
        return LocalDateTime.parse(text(node, field));
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}

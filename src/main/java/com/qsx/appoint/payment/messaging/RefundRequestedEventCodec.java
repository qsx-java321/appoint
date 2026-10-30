package com.qsx.appoint.payment.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.RefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
class RefundRequestedEventCodec {
    private final ObjectMapper objectMapper;

    RefundRequestedEvent read(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode payload = root.path("payload");
            return new RefundRequestedEvent(root.path("eventId").asText(null),
                    root.path("eventVersion").asInt(), longValue(payload.get("refundId")),
                    payload.path("refundNo").asText(null));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) throw businessException;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private Long longValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        return value.isNumber() ? value.longValue() : Long.valueOf(value.asText());
    }
}

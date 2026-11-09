package com.qsx.appoint.payment.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.RefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 退款请求JSON解码器，提取worker实际需要的eventId、版本及退款ID/编号。
 * <p>不在此声称完整校验整个envelope；v1、UUID和必填退款字段由worker校验。
 */
@Component
@RequiredArgsConstructor
class RefundRequestedEventCodec {
    private final ObjectMapper objectMapper;

    /**
     * 提取退款事件所需字段，解析错误转永久无效契约。
     * @param body Rabbit消息正文
     * @return 退款最小事件；字段缺失可能为null，worker负责最终业务校验
     * @throws com.qsx.appoint.common.BusinessException JSON/ID解析失败，错误码VALIDATION_ERROR
     */
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
    /** 兼容JSON数值或Long文本ID；缺失/null保持null交worker校验，非法文本抛解析异常。 */
    private Long longValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        return value.isNumber() ? value.longValue() : Long.valueOf(value.asText());
    }
}

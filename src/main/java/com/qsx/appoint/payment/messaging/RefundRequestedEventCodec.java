package com.qsx.appoint.payment.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.qsx.appoint.common.messaging.MessageJsonFields;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.RefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 退款请求JSON解码器，提取worker实际需要的eventId、版本及退款ID/编号。
 * <p>校验 envelope 类型/版本/时间/支付聚合关联；UUID 和退款资格由 worker 复验。
 */
@Component
@RequiredArgsConstructor
class RefundRequestedEventCodec {
    private final ObjectMapper objectMapper;

    /**
     * 提取退款事件所需字段，解析错误转永久无效契约。
     * @param body Rabbit消息正文
     * @return 退款最小事件；必需字段已验证，worker继续检查业务资格
     * @throws com.qsx.appoint.common.BusinessException JSON/ID解析失败，错误码VALIDATION_ERROR
     */
    RefundRequestedEvent read(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!"RefundRequested".equals(MessageJsonFields.text(root,"eventType"))) throw MessageJsonFields.invalid();
            MessageJsonFields.versionOne(root);
            MessageJsonFields.time(root,"occurredAt");
            JsonNode payload = root.required("payload");
            if (!payload.isObject()) throw MessageJsonFields.invalid();
            if (!MessageJsonFields.positiveId(root,"aggregateId").equals(
                    MessageJsonFields.positiveId(payload,"paymentOrderId"))) throw MessageJsonFields.invalid();
            return new RefundRequestedEvent(MessageJsonFields.text(root,"eventId"), 1,
                    MessageJsonFields.positiveId(payload,"refundId"), MessageJsonFields.text(payload,"refundNo"));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) throw businessException;
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}

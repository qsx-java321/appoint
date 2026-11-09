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

/**
 * PaymentSucceeded v1的JSON解码入口，校验类型/版本、结构及聚合支付关联。
 * <p>兼容数字或字符串ID；UUID、正金额等业务约束由Processor再次校验。
 */
@Component
@RequiredArgsConstructor
public class PaymentSucceededEventCodec {
    private final ObjectMapper objectMapper;

    /**
     * 解码支付成功v1 envelope，验证类型/版本并要求aggregateId等于payload.paymentOrderId。
     * @param body UTF-8 JSON消息正文
     * @return 交给事务Processor验证并处理的业务事件
     * @throws com.qsx.appoint.common.BusinessException 格式/字段/关联解析失败，错误码VALIDATION_ERROR
     */
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
    /** 读取必填非空文本，拒绝缺失、非字符串或空白内容。 */
    private String text(JsonNode node, String field) {
        String value = node.required(field).textValue();
        if (value == null || value.isBlank()) throw invalid();
        return value;
    }
    /** 按现有JsonNode整数可转换语义读取版本，再由入口比较是否为1。 */
    private int integer(JsonNode node, String field) {
        if (!node.required(field).canConvertToInt()) throw invalid();
        return node.required(field).intValue();
    }
    /** 兼容JSON数值或可解析Long文本ID；其他形态归为无效契约。 */
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
    /** 金额要求JSON数值，不能把文本金额隐式转成资金事实。 */
    private BigDecimal decimal(JsonNode node, String field) {
        if (!node.required(field).isNumber()) throw invalid();
        return node.required(field).decimalValue();
    }
    /** 按现有无偏移LocalDateTime格式解析时间，不在解码器重解释时区。 */
    private LocalDateTime time(JsonNode node, String field) {
        return LocalDateTime.parse(text(node, field));
    }
    /** 统一构造永久无效契约错误，供监听器拒绝进入DLQ。 */
    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}

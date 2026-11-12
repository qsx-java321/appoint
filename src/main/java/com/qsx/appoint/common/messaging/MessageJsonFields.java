package com.qsx.appoint.common.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import java.time.LocalDateTime;

/** 消息字段的严格读取规则；只校验 JSON 形态，不决定支付或退款业务。 */
public final class MessageJsonFields {
    private MessageJsonFields() {}
    /** ID 接受正 Long 范围的整数数字/十进制文本，禁止浮点截断和越界默认值。 */
    public static Long positiveId(JsonNode node, String field) {
        JsonNode value = node.required(field);
        long id;
        if (value.isIntegralNumber() && value.canConvertToLong()) id = value.longValue();
        else if (value.isTextual() && value.textValue().matches("[0-9]+")) {
            try { id = Long.parseLong(value.textValue()); }
            catch (NumberFormatException exception) { throw invalid(); }
        } else throw invalid();
        if (id <= 0) throw invalid();
        return id;
    }
    /** 版本不允许字符串/浮点隐式转换，只接受当前整数1。 */
    public static void versionOne(JsonNode root) {
        JsonNode value = root.required("eventVersion");
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() != 1) throw invalid();
    }
    public static String text(JsonNode root, String field) {
        String value = root.required(field).textValue();
        if (value == null || value.isBlank()) throw invalid();
        return value;
    }
    public static LocalDateTime time(JsonNode root, String field) {
        return LocalDateTime.parse(text(root,field));
    }
    public static BusinessException invalid() { return new BusinessException(ErrorCode.VALIDATION_ERROR); }
}

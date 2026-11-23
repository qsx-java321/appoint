package com.qsx.appoint.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/**
 * 内部事件 JSON 编解码；与 HTTP ObjectMapper 分离，避免 Long 转字符串影响内部往返。
 */
@Component
public class NotificationPayloadCodec {
    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public String encode(NotificationPayload payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("无法编码通知事件", exception);
        }
    }

    public NotificationPayload decode(String payload) {
        try {
            return json.readValue(payload, NotificationPayload.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析通知事件", exception);
        }
    }
}

package com.qsx.appoint.notification.dto;

import java.time.LocalDateTime;

/** 消息中心列表项；只暴露浏览所需字段。 */
public record NotificationListItemResponse(
        Long id,
        String type,
        String title,
        String patientName,
        LocalDateTime occurredAt) {
}

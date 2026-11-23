package com.qsx.appoint.notification.dto;

import java.time.LocalDateTime;

/** 消息详情；详情正文为事件生成时保存的快照，并给出可跳转的预约单ID。 */
public record NotificationDetailResponse(
        Long id,
        String type,
        String title,
        String patientName,
        LocalDateTime occurredAt,
        String detail,
        Long appointmentId) {
}

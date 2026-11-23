package com.qsx.appoint.notification.application;

import com.qsx.appoint.notification.domain.NotificationType;

import java.time.LocalDateTime;

/**
 * Outbox 与 RabbitMQ 之间传输的完整通知事实。
 * <p>保存事件生成时已渲染的内容，消费端不再查询预约、就诊人或目录。
 */
public record NotificationPayload(
        String eventId,
        NotificationType type,
        Long appointmentId,
        Long userId,
        String title,
        String patientName,
        LocalDateTime occurredAt,
        String detail) {
}

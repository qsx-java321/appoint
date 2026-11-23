package com.qsx.appoint.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 与业务写操作同事务提交的待发布通知事件。
 * <p>发布器持续重试，只有PENDING和PUBLISHED两种状态，不设置永久失败终态。
 */
@Getter
@Setter
@TableName("notification_outbox_events")
public class NotificationOutboxEvent {
    private Long id;
    private String eventId;
    private Long appointmentOrderId;
    private String notificationType;
    private String payload;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}

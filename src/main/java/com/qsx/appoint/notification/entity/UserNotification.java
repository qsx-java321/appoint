package com.qsx.appoint.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 消息中心已入库通知；标题、就诊人、发生时间和详情均为事件生成时的快照。
 */
@Getter
@Setter
@TableName("user_notifications")
public class UserNotification {
    private Long id;
    private String eventId;
    private Long userId;
    private Long appointmentOrderId;
    private String notificationType;
    private String title;
    private String patientName;
    private LocalDateTime occurredAt;
    private String detail;
    private LocalDateTime createdAt;
}

package com.qsx.appoint.notification.application;

import com.qsx.appoint.notification.entity.UserNotification;
import com.qsx.appoint.notification.mapper.UserNotificationMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 消息中心唯一的消费写入入口；业务节点唯一键承担最终去重。 */
@Service
class DefaultNotificationInboxWriter implements NotificationInboxWriter {
    private final UserNotificationMapper notifications;
    private final Clock notificationClock;

    DefaultNotificationInboxWriter(UserNotificationMapper notifications,
                                   @Qualifier("notificationClock") Clock notificationClock) {
        this.notifications = notifications;
        this.notificationClock = notificationClock;
    }

    @Override
    @Transactional
    public void write(NotificationPayload payload) {
        require(payload);
        UserNotification notification = new UserNotification();
        notification.setEventId(payload.eventId());
        notification.setUserId(payload.userId());
        notification.setAppointmentOrderId(payload.appointmentId());
        notification.setNotificationType(payload.type().name());
        notification.setTitle(payload.title());
        notification.setPatientName(payload.patientName());
        notification.setOccurredAt(payload.occurredAt());
        notification.setDetail(payload.detail());
        notification.setCreatedAt(LocalDateTime.now(notificationClock).truncatedTo(ChronoUnit.SECONDS));
        notifications.insertIgnore(notification);
    }

    private void require(NotificationPayload payload) {
        if (payload == null || blank(payload.eventId()) || payload.type() == null
                || payload.appointmentId() == null || payload.userId() == null
                || blank(payload.title()) || blank(payload.patientName())
                || payload.occurredAt() == null || blank(payload.detail())) {
            throw new IllegalArgumentException("通知事件缺少必要字段");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

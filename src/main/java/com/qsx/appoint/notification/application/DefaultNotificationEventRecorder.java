package com.qsx.appoint.notification.application;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.notification.domain.NotificationType;
import com.qsx.appoint.notification.entity.NotificationOutboxEvent;
import com.qsx.appoint.notification.mapper.NotificationOutboxEventMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 业务事务内的通知事件记录器；不直接连接RabbitMQ。 */
@Service
class DefaultNotificationEventRecorder implements NotificationEventRecorder {
    private final AppointmentNotificationSource source;
    private final NotificationMessageFactory factory;
    private final NotificationPayloadCodec codec;
    private final NotificationOutboxEventMapper outbox;
    private final Clock notificationClock;

    DefaultNotificationEventRecorder(AppointmentNotificationSource source, NotificationMessageFactory factory,
                                     NotificationPayloadCodec codec, NotificationOutboxEventMapper outbox,
                                     @Qualifier("notificationClock") Clock notificationClock) {
        this.source = source;
        this.factory = factory;
        this.codec = codec;
        this.outbox = outbox;
        this.notificationClock = notificationClock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long appointmentOrderId, NotificationType type, LocalDateTime occurredAt) {
        AppointmentNotificationSnapshot snapshot = source.find(appointmentOrderId);
        if (snapshot == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        NotificationDraft draft = factory.create(snapshot, type, occurredAt);
        String eventId = UUID.randomUUID().toString();
        NotificationPayload payload = new NotificationPayload(eventId, type, appointmentOrderId, snapshot.userId(),
                draft.title(), draft.patientName(), occurredAt, draft.detail());
        LocalDateTime now = LocalDateTime.now(notificationClock).truncatedTo(ChronoUnit.SECONDS);
        NotificationOutboxEvent event = new NotificationOutboxEvent();
        event.setEventId(eventId);
        event.setAppointmentOrderId(appointmentOrderId);
        event.setNotificationType(type.name());
        event.setPayload(codec.encode(payload));
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        outbox.insertIgnore(event);
    }
}

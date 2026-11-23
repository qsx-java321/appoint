package com.qsx.appoint.notification.application;

import com.qsx.appoint.notification.entity.NotificationOutboxEvent;
import com.qsx.appoint.notification.mapper.NotificationOutboxEventMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用可控发布端口验证成功标记、失败累计和指数退避。 */
class NotificationOutboxPublisherTest {
    private final NotificationOutboxEventMapper outbox = mock(NotificationOutboxEventMapper.class);
    private final NotificationMessagePublisher publisher = mock(NotificationMessagePublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final NotificationOutboxPublisher subject = new NotificationOutboxPublisher(outbox, publisher, clock);

    @Test
    void successMarksPendingEventPublishedWithAttemptCount() throws Exception {
        var event = event(7L, "event-1", 0);
        when(outbox.selectPending(LocalDateTime.parse("2026-11-23T08:00:00"), 50)).thenReturn(List.of(event));

        subject.publishPending();

        verify(publisher).publish("event-1", "{}");
        verify(outbox).markPublished(7L, 1, LocalDateTime.parse("2026-11-23T08:00:00"));
    }

    @Test
    void failureAccumulatesAttemptsAndUsesExponentialBackoff() throws Exception {
        var event = event(8L, "event-2", 2);
        when(outbox.selectPending(any(), anyInt())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(eq("event-2"), anyString());

        subject.publishPending();

        verify(outbox).markFailed(eq(8L), eq(3), eq(LocalDateTime.parse("2026-11-23T08:00:05")),
                org.mockito.ArgumentMatchers.contains("broker down"));
    }

    @Test
    void publishedEventIsNotSelectedOrPublishedAgain() throws Exception {
        var event = event(9L, "event-3", 0);
        when(outbox.selectPending(any(), anyInt())).thenReturn(List.of(event)).thenReturn(List.of());

        subject.publishPending();
        subject.publishPending();

        verify(publisher).publish("event-3", "{}");
        verify(outbox).markPublished(9L, 1, LocalDateTime.parse("2026-11-23T08:00:00"));
    }

    @Test
    void backoffStartsAtOneSecondIsExponentialAndCapsAtSixty() {
        assertThat(NotificationOutboxPublisher.backoffSeconds(1)).isEqualTo(1);
        assertThat(NotificationOutboxPublisher.backoffSeconds(2)).isEqualTo(2);
        assertThat(NotificationOutboxPublisher.backoffSeconds(3)).isEqualTo(4);
        assertThat(NotificationOutboxPublisher.backoffSeconds(4)).isEqualTo(8);
        assertThat(NotificationOutboxPublisher.backoffSeconds(6)).isEqualTo(32);
        assertThat(NotificationOutboxPublisher.backoffSeconds(7)).isEqualTo(60);
        assertThat(NotificationOutboxPublisher.backoffSeconds(100)).isEqualTo(60);
    }

    private NotificationOutboxEvent event(Long id, String eventId, int attempts) {
        var event = new NotificationOutboxEvent();
        event.setId(id);
        event.setEventId(eventId);
        event.setPayload("{}");
        event.setAttempts(attempts);
        return event;
    }
}

package com.qsx.appoint.notification.messaging;

import com.qsx.appoint.notification.application.NotificationInboxWriter;
import com.qsx.appoint.notification.application.NotificationPayload;
import com.qsx.appoint.notification.application.NotificationPayloadCodec;
import com.qsx.appoint.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 无效通知最多投递4次，退避1秒、2秒、4秒后失败，由监听入口转入死信队列。 */
class NotificationConsumerTest {
    private final NotificationInboxWriter inbox = mock(NotificationInboxWriter.class);
    private final NotificationPayloadCodec codec = mock(NotificationPayloadCodec.class);
    private final List<Long> pauses = new ArrayList<>();
    private final NotificationConsumer consumer = new NotificationConsumer(inbox, codec) {
        @Override
        void pause(long millis) {
            pauses.add(millis);
        }
    };
    private final NotificationPayload payload = new NotificationPayload("event-1",
            NotificationType.CHECKED_IN, 31L, 12L, "签到成功", "就诊人甲",
            LocalDateTime.parse("2026-11-13T07:35:00"), "详情");

    @Test
    void invalidPayloadIsAttemptedFourTimesWithSpecifiedBackoff() {
        when(codec.decode("{}")).thenReturn(payload);
        doThrow(new IllegalArgumentException("bad payload")).when(inbox).write(any());

        assertThatThrownBy(() -> consumer.processWithRetry("{}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("bad payload");

        verify(inbox, times(4)).write(payload);
        assertThat(pauses).containsExactly(1000L, 2000L, 4000L);
    }

    @Test
    void validPayloadIsWrittenOnceWithoutRetryDelay() {
        when(codec.decode("{}")).thenReturn(payload);

        consumer.processWithRetry("{}");

        verify(inbox).write(payload);
        assertThat(pauses).isEmpty();
    }
}

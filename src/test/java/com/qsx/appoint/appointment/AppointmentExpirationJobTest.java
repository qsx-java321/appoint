package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.config.ExpirationProperties;
import com.qsx.appoint.appointment.job.AppointmentExpirationJob;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.mapper.ExpirationCandidate;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 直接构造扫描器并模拟单项失败，验证后续候选仍被处理；数据库竞争由MySQL测试负责。 */
@ExtendWith(OutputCaptureExtension.class)
class AppointmentExpirationJobTest {
    @Test
    void oneFailureDoesNotPreventLaterCandidatesAndResultsAreCounted(CapturedOutput output) {
        AppointmentOrderMapper mapper = mock(AppointmentOrderMapper.class);
        BookingLifecycleCoordinator lifecycle = mock(BookingLifecycleCoordinator.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T06:00:00Z"), ZoneId.of("Asia/Shanghai"));
        ExpirationCandidate first = candidate(1L, LocalDateTime.of(2026, 10, 26, 13, 0));
        ExpirationCandidate second = candidate(2L, LocalDateTime.of(2026, 10, 26, 13, 1));
        ExpirationCandidate third = candidate(3L, LocalDateTime.of(2026, 10, 26, 13, 2));
        when(mapper.selectExpirationCandidates(any(), isNull(), isNull(), eq(100)))
                .thenReturn(List.of(first, second, third));
        when(lifecycle.expireIfDue(1L)).thenThrow(new IllegalStateException("broken fixture"));
        when(lifecycle.expireIfDue(2L)).thenReturn(false);
        when(lifecycle.expireIfDue(3L)).thenReturn(true);
        AppointmentExpirationJob job = new AppointmentExpirationJob(mapper, lifecycle,
                new ExpirationProperties(true, Duration.ofSeconds(30), 100), clock);

        job.scanExpiredAppointments();

        var processing = inOrder(lifecycle);
        processing.verify(lifecycle).expireIfDue(1L);
        processing.verify(lifecycle).expireIfDue(2L);
        processing.verify(lifecycle).expireIfDue(3L);
        assertThat(output).contains("过期预约处理失败，appointmentOrderId=1",
                "scanned=3, expired=1, skipped=1, failed=1");
    }

    private ExpirationCandidate candidate(Long id, LocalDateTime expiresAt) {
        ExpirationCandidate candidate = new ExpirationCandidate();
        candidate.setId(id);
        candidate.setPaymentExpiresAt(expiresAt);
        return candidate;
    }
}

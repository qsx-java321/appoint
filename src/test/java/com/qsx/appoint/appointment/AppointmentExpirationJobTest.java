package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.config.ExpirationProperties;
import com.qsx.appoint.appointment.job.AppointmentExpirationJob;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.mapper.ExpirationCandidate;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppointmentExpirationJobTest {
    @Test
    void oneFailureDoesNotPreventLaterCandidates() {
        AppointmentOrderMapper mapper = mock(AppointmentOrderMapper.class);
        BookingLifecycleCoordinator lifecycle = mock(BookingLifecycleCoordinator.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T06:00:00Z"), ZoneId.of("Asia/Shanghai"));
        ExpirationCandidate first = candidate(1L, LocalDateTime.of(2026, 10, 26, 13, 0));
        ExpirationCandidate second = candidate(2L, LocalDateTime.of(2026, 10, 26, 13, 1));
        when(mapper.selectExpirationCandidates(any(), isNull(), isNull(), eq(100)))
                .thenReturn(List.of(first, second));
        when(lifecycle.expireIfDue(1L)).thenThrow(new IllegalStateException("broken fixture"));
        when(lifecycle.expireIfDue(2L)).thenReturn(true);
        AppointmentExpirationJob job = new AppointmentExpirationJob(mapper, lifecycle,
                new ExpirationProperties(true, Duration.ofSeconds(30), 100), clock);

        job.scanExpiredAppointments();

        verify(lifecycle).expireIfDue(1L);
        verify(lifecycle).expireIfDue(2L);
    }

    private ExpirationCandidate candidate(Long id, LocalDateTime expiresAt) {
        ExpirationCandidate candidate = new ExpirationCandidate();
        candidate.setId(id);
        candidate.setPaymentExpiresAt(expiresAt);
        return candidate;
    }
}

package com.qsx.appoint.visit.application;

import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.catalog.service.DoctorAccountService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.notification.application.NotificationEventRecorder;
import com.qsx.appoint.visit.entity.VisitRecord;
import com.qsx.appoint.visit.mapper.VisitRecordMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 对象替身验证单次秒精度采样及部署前签到事实兼容；真实约束与事务由集成测试负责。 */
class VisitServiceTest {
    private final AppointmentLifecycle appointments = mock(AppointmentLifecycle.class);
    private final DoctorAccountService doctors = mock(DoctorAccountService.class);
    private final VisitRecordMapper records = mock(VisitRecordMapper.class);
    private final NotificationEventRecorder notifications = mock(NotificationEventRecorder.class);
    private final Clock clock = mock(Clock.class);
    private final DefaultVisitService visits = new DefaultVisitService(
            appointments, doctors, records, notifications, clock);

    private void confirmed() {
        when(appointments.find(1L)).thenReturn(new AppointmentLifecycleCase(1L, 2L, 3L, AppointmentStatus.CONFIRMED,
                LocalDateTime.parse("2026-11-12T08:15:00"), LocalDate.of(2026, 11, 13), LocalTime.of(8, 0),
                1, 4L, LocalTime.of(10, 0)));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Shanghai"));
    }

    @Test
    void oneSecondPrecisionSampleDeterminesEligibilityAndAllInsertTimes() {
        confirmed();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-13T01:59:59.999Z"), Instant.parse("2026-11-13T02:00:00Z"));
        visits.checkIn(2L, 1L);
        var saved = org.mockito.ArgumentCaptor.forClass(VisitRecord.class);
        verify(records).insert(saved.capture());
        var time = LocalDateTime.parse("2026-11-13T09:59:59");
        assertThat(saved.getValue().getCheckedInAt()).isEqualTo(time);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(time);
        assertThat(saved.getValue().getUpdatedAt()).isEqualTo(time);
        verify(clock, times(1)).instant();
    }

    @Test
    void midnightCheckInIsNotRevokedAndCanStartOnNextDay() {
        confirmed();
        var legacy = new VisitRecord();
        legacy.setStatus("WAITING");
        legacy.setCheckedInAt(LocalDateTime.parse("2026-11-13T00:00:00"));
        when(records.selectByAppointmentOrderId(1L)).thenReturn(legacy);
        assertThatThrownBy(() -> visits.checkIn(2L, 1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VISIT_STATE_ERROR));
        verifyNoInteractions(clock);
        when(doctors.requireDoctorId(5L)).thenReturn(4L);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-14T00:00:00Z"));
        var nextDay = LocalDateTime.parse("2026-11-14T08:00:00");
        when(records.startIfWaiting(1L, nextDay)).thenReturn(1);
        visits.startVisit(5L, 1L);
        verify(records).startIfWaiting(1L, nextDay);
        verify(records, never()).insert(any(VisitRecord.class));
        assertThat(legacy.getCheckedInAt()).isEqualTo(LocalDateTime.parse("2026-11-13T00:00:00"));
    }
}

package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.bookingquery.persistence.BookingQueryMapper;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实查询服务配合Mapper/Clock替身，验证列表统一采时和权限校验顺序；SQL由集成测试负责。 */
class BookingQueryServiceTest {
    private final BookingQueryMapper mapper = mock(BookingQueryMapper.class);
    private final Clock clock = mock(Clock.class);
    private final AppointmentResponseAssembler assembler = new AppointmentResponseAssembler(new BookingWorkflowProjector());
    private final DefaultBookingQueryService service = new DefaultBookingQueryService(mapper, clock, assembler);

    @Test
    void listUsesOneTruncatedServerTimeForAllRows() {
        var first = validRecord();
        var second = validRecord();
        when(mapper.selectByUserId(1L)).thenReturn(List.of(first, second));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Shanghai"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-09T00:00:00.999Z"), Instant.parse("2026-11-09T00:00:01Z"));
        var responses = service.listMine(1L);
        var now = LocalDateTime.of(2026, 11, 9, 8, 0);
        assertThat(responses).hasSize(2).allSatisfy(response -> assertThat(response.getServerTime()).isEqualTo(now));
        verify(clock, times(1)).instant();
    }

    @Test
    void confirmedListUsesOneSampleEvenWhenClockWouldCrossCheckInDeadline() {
        var first = validRecord();
        var second = validRecord();
        for (var row : List.of(first, second)) {
            row.setStatus("CONFIRMED"); row.setPaymentStatus("PAID");
            row.setAppointmentDate(java.time.LocalDate.of(2026, 11, 9));
            row.setEndTime(java.time.LocalTime.of(10, 0));
        }
        when(mapper.selectByUserId(1L)).thenReturn(List.of(first, second));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Shanghai"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-09T01:59:59.999Z"), Instant.parse("2026-11-09T02:00:00Z"));
        assertThat(service.listMine(1L)).hasSize(2).allSatisfy(row -> {
            assertThat(row.getVisit().canCheckIn()).isTrue();
            assertThat(row.getServerTime()).isEqualTo(LocalDateTime.parse("2026-11-09T09:59:59"));
        });
        verify(clock, times(1)).instant();
    }

    private BookingQueryRecord validRecord() {
        var record = new BookingQueryRecord();
        record.setAppointmentUserId(1L);
        record.setPaymentUserId(1L);
        record.setPaymentOrderId(2L);
        record.setStatus("PENDING_PAYMENT");
        record.setPaymentStatus("UNPAID");
        record.setAppointmentDate(java.time.LocalDate.of(2026, 11, 10));
        record.setStartTime(java.time.LocalTime.of(8, 0));
        record.setPatientDocumentNo("110105199001010010");
        return record;
    }

    @Test
    void ownershipAndMissingOrderErrorsPrecedePaymentIntegrityAndClock() {
        assertThatThrownBy(() -> service.getOwned(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        var record = new BookingQueryRecord();
        record.setAppointmentUserId(3L);
        when(mapper.selectById(2L)).thenReturn(record);
        assertThatThrownBy(() -> service.getOwned(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(clock);
    }
}

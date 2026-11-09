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

class BookingQueryServiceTest {
    private final BookingQueryMapper mapper = mock(BookingQueryMapper.class);
    private final Clock clock = mock(Clock.class);
    private final AppointmentResponseAssembler assembler = mock(AppointmentResponseAssembler.class);
    private final DefaultBookingQueryService service = new DefaultBookingQueryService(mapper, clock, assembler);

    @Test
    void listUsesOneTruncatedServerTimeForAllRows() {
        var first = new BookingQueryRecord();
        var second = new BookingQueryRecord();
        when(mapper.selectByUserId(1L)).thenReturn(List.of(first, second));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Shanghai"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-09T00:00:00.999Z"), Instant.parse("2026-11-09T00:00:01Z"));
        service.listMine(1L);
        var now = LocalDateTime.of(2026, 11, 9, 8, 0);
        verify(assembler).assemble(first, now);
        verify(assembler).assemble(second, now);
        verify(clock, times(1)).instant();
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
        verifyNoInteractions(assembler, clock);
    }
}

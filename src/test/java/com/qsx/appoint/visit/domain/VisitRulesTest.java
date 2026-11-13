package com.qsx.appoint.visit.domain;

import com.qsx.appoint.appointment.domain.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import java.time.LocalTime;
import static org.assertj.core.api.Assertions.assertThat;

/** 纯对象验证四场签到窗口、跨日及历史快照；不读取系统时钟或数据库。 */
class VisitRulesTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 13);

    @ParameterizedTest
    @CsvSource({"08:00,10:00,07:30", "10:30,12:00,10:00", "14:00,16:00,13:30", "16:30,18:00,16:00"})
    void fourSessionsIncludeOpeningButExcludeClosing(String startValue, String endValue, String openValue) {
        var start = LocalTime.parse(startValue);
        var end = LocalTime.parse(endValue);
        var opens = DATE.atTime(LocalTime.parse(openValue));
        var closes = DATE.atTime(end);
        assertThat(VisitRules.checkInOpensAt(DATE, start)).isEqualTo(opens);
        assertThat(VisitRules.checkInClosesAt(DATE, end)).isEqualTo(closes);
        for (var time : new java.time.LocalDateTime[]{opens.minusSeconds(1), DATE.atStartOfDay(), DATE.minusDays(1).atTime(start),
                closes, closes.plusSeconds(1), DATE.plusDays(1).atTime(start)}) {
            assertThat(VisitRules.canCheckIn(AppointmentStatus.CONFIRMED, DATE, start, end, VisitStatus.NOT_CHECKED_IN, time)).isFalse();
        }
        for (var time : new java.time.LocalDateTime[]{opens, DATE.atTime(start).plusSeconds(1), closes.minusSeconds(1)}) {
            assertThat(VisitRules.canCheckIn(AppointmentStatus.CONFIRMED, DATE, start, end, VisitStatus.NOT_CHECKED_IN, time)).isTrue();
        }
    }

    @Test
    void onlyConfirmedAndUnsignedCanCheckInEvenInsideWindow() {
        for (var status : AppointmentStatus.values()) {
            for (var progress : VisitStatus.values()) {
                assertThat(VisitRules.canCheckIn(status, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0), progress, DATE.atTime(8, 0)))
                        .isEqualTo(status == AppointmentStatus.CONFIRMED && progress == VisitStatus.NOT_CHECKED_IN);
            }
        }
    }

    @Test
    void historicalSnapshotsDetermineWindowWithoutCurrentSessionEnum() {
        assertThat(VisitRules.checkInOpensAt(DATE, LocalTime.of(9, 15))).isEqualTo(DATE.atTime(8, 45));
        assertThat(VisitRules.canCheckIn(AppointmentStatus.CONFIRMED, DATE, LocalTime.of(9, 15), LocalTime.of(12, 30),
                VisitStatus.NOT_CHECKED_IN, DATE.atTime(12, 15))).isTrue();
        assertThat(VisitRules.checkInOpensAt(DATE, LocalTime.of(0, 15))).isEqualTo(DATE.minusDays(1).atTime(23, 45));
    }
}

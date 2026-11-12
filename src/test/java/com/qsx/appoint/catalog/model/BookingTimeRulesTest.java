package com.qsx.appoint.catalog.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 直接验证上海日期窗口、严格截止和支付期限计算；边界时间显式给定。 */
class BookingTimeRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Test
    void threeDatesRollTogetherAtShanghaiMidnight() {
        var before = TODAY.atTime(23, 59, 59);
        assertThat(BookingTimeRules.selectableDates(before))
                .containsExactly(TODAY, TODAY.plusDays(1), TODAY.plusDays(2));
        assertThat(BookingTimeRules.selectableDates(before.plusSeconds(1)))
                .containsExactly(TODAY.plusDays(1), TODAY.plusDays(2), TODAY.plusDays(3));
    }

    @ParameterizedTest
    @EnumSource(AppointmentSession.class)
    void eachSessionAcceptsBeforeButNotAtOrAfterItsCutoff(AppointmentSession session) {
        var cutoff = BookingTimeRules.bookingClosesAt(TODAY, session.startTime());
        assertThat(cutoff).isEqualTo(TODAY.atTime(session.startTime()).minusMinutes(15));
        assertThat(BookingTimeRules.canCreate(TODAY, session.startTime(), cutoff.minusSeconds(1))).isTrue();
        assertThat(BookingTimeRules.canCreate(TODAY, session.startTime(), cutoff)).isFalse();
        assertThat(BookingTimeRules.canCreate(TODAY, session.startTime(), cutoff.plusSeconds(1))).isFalse();
    }

    @Test
    void creationChecksBothDateWindowAndTodaySessionTime() {
        var now = TODAY.atTime(10, 5);
        var afternoon = AppointmentSession.AFTERNOON_FIRST.startTime();
        assertThat(BookingTimeRules.canCreate(TODAY.minusDays(1), afternoon, now)).isFalse();
        assertThat(BookingTimeRules.canCreate(TODAY, afternoon, now)).isTrue();
        assertThat(BookingTimeRules.canCreate(TODAY.plusDays(1), afternoon, now)).isTrue();
        assertThat(BookingTimeRules.canCreate(TODAY.plusDays(2), afternoon, now)).isTrue();
        assertThat(BookingTimeRules.canCreate(TODAY.plusDays(3), afternoon, now)).isFalse();
        assertThat(BookingTimeRules.canCreate(TODAY, AppointmentSession.MORNING_FIRST.startTime(), now)).isFalse();
        assertThat(BookingTimeRules.canCreate(null, afternoon, now)).isFalse();
    }

    @Test
    void originalTomorrowCanStillBeCreatedAfterMidnight() {
        var tomorrow = TODAY.plusDays(1);
        assertThat(BookingTimeRules.canCreate(tomorrow, AppointmentSession.MORNING_FIRST.startTime(),
                TODAY.atTime(23, 59, 59))).isTrue();
        assertThat(BookingTimeRules.canCreate(tomorrow, AppointmentSession.MORNING_FIRST.startTime(),
                tomorrow.atStartOfDay())).isTrue();
    }

    @Test
    void paymentDeadlineIsPerOrderAndCanCrossMidnight() {
        var timeout = Duration.ofMinutes(15);
        assertThat(BookingTimeRules.paymentExpiresAt(TODAY.atTime(13, 40), TODAY.atTime(14, 0), timeout))
                .isEqualTo(TODAY.atTime(13, 55));
        assertThat(BookingTimeRules.paymentExpiresAt(TODAY.atTime(10, 0), TODAY.plusDays(2).atTime(14, 0), timeout))
                .isEqualTo(TODAY.atTime(10, 15));
        assertThat(BookingTimeRules.paymentExpiresAt(TODAY.atTime(23, 55), TODAY.plusDays(1).atTime(8, 0), timeout))
                .isEqualTo(TODAY.plusDays(1).atTime(0, 10));
    }

    @Test
    void paymentDeadlineNeverExceedsSessionStartEvenWithLongerConfiguredTimeout() {
        assertThat(BookingTimeRules.paymentExpiresAt(TODAY.atTime(13, 40), TODAY.atTime(14, 0),
                Duration.ofMinutes(30))).isEqualTo(TODAY.atTime(14, 0));
    }
}

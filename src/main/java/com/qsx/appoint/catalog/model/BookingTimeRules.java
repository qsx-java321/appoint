package com.qsx.appoint.catalog.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 预约场次的共享时间规则；输入时间均为上海业务时间，由调用方统一采样。 */
public final class BookingTimeRules {
    public static final Duration MINIMUM_BOOKING_ADVANCE = Duration.ofMinutes(15);
    private static final int WINDOW_DAYS = 3;

    private BookingTimeRules() { }

    /** 日期入口始终包含今天、明天、后天，查询不因当天已截止而减少日期。 */
    public static List<LocalDate> selectableDates(LocalDateTime serverTime) {
        LocalDate today = serverTime.toLocalDate();
        return today.datesUntil(today.plusDays(WINDOW_DAYS)).toList();
    }

    public static LocalDateTime bookingClosesAt(LocalDate appointmentDate, LocalTime startTime) {
        return appointmentDate.atTime(startTime).minus(MINIMUM_BOOKING_ADVANCE);
    }

    /** 同时校验三天窗口和场次截止；恰好到截止时间已经不能创建。 */
    public static boolean canCreate(LocalDate appointmentDate, LocalTime startTime,
                                    LocalDateTime acceptanceTime) {
        LocalDate today = acceptanceTime.toLocalDate();
        return appointmentDate != null && !appointmentDate.isBefore(today)
                && appointmentDate.isBefore(today.plusDays(WINDOW_DAYS))
                && acceptanceTime.isBefore(bookingClosesAt(appointmentDate, startTime));
    }

    /** 只在新订单创建时计算，持久化后不能随查询、支付尝试或日期滚动重算。 */
    public static LocalDateTime paymentExpiresAt(LocalDateTime createdAt, LocalDateTime sessionStartsAt,
                                                Duration paymentTimeout) {
        LocalDateTime timeoutAt = createdAt.plus(paymentTimeout);
        return timeoutAt.isBefore(sessionStartsAt) ? timeoutAt : sessionStartsAt;
    }

    /** 最晚支付时刻当秒仍可支付。 */
    public static boolean isPaymentExpired(LocalDateTime paymentDeadline, LocalDateTime serverTime) {
        return paymentDeadline != null && serverTime.isAfter(paymentDeadline);
    }
}

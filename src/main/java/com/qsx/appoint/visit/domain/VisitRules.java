package com.qsx.appoint.visit.domain;

import com.qsx.appoint.appointment.domain.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 写入资格与查询按钮共享的纯规则，不读取时钟、数据库或渠道。 */
public final class VisitRules {
    private VisitRules() {}
    public static LocalDateTime checkInOpensAt(LocalDate date, LocalTime startTime) {
        return date.atTime(startTime).minusMinutes(30);
    }
    public static LocalDateTime checkInClosesAt(LocalDate date, LocalTime endTime) {
        return date.atTime(endTime);
    }
    public static boolean canCheckIn(AppointmentStatus appointmentStatus, LocalDate appointmentDate,
                                     LocalTime startTime, LocalTime endTime, VisitStatus progress,
                                     LocalDateTime now) {
        return appointmentStatus == AppointmentStatus.CONFIRMED && progress == VisitStatus.NOT_CHECKED_IN
                && !now.isBefore(checkInOpensAt(appointmentDate, startTime))
                && now.isBefore(checkInClosesAt(appointmentDate, endTime));
    }
    public static boolean canStart(VisitStatus progress) { return progress == VisitStatus.WAITING; }
    public static boolean canComplete(VisitStatus progress) { return progress == VisitStatus.IN_PROGRESS; }
}

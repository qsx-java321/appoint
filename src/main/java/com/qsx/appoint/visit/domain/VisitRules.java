package com.qsx.appoint.visit.domain;

import com.qsx.appoint.appointment.domain.AppointmentStatus;
import java.time.LocalDate;

/** 写入资格与查询按钮共享的纯规则，不读取时钟、数据库或渠道。 */
public final class VisitRules {
    private VisitRules() {}
    public static boolean canCheckIn(AppointmentStatus appointmentStatus, LocalDate appointmentDate,
                                     VisitStatus progress, LocalDate today) {
        return appointmentStatus == AppointmentStatus.CONFIRMED && appointmentDate.equals(today)
                && progress == VisitStatus.NOT_CHECKED_IN;
    }
    public static boolean canStart(VisitStatus progress) { return progress == VisitStatus.WAITING; }
    public static boolean canComplete(VisitStatus progress) { return progress == VisitStatus.IN_PROGRESS; }
}

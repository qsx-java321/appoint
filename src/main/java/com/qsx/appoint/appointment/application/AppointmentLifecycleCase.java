package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.domain.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 稳定业务事实；写入只接受当前事务lock返回的原对象，不能使用find或跨事务副本。 */
public record AppointmentLifecycleCase(
        Long id,
        Long userId,
        Long scheduleId,
        AppointmentStatus status,
        LocalDateTime paymentExpiresAt,
        LocalDate appointmentDate,
        LocalTime startTime,
        Integer visitNumber) {
}

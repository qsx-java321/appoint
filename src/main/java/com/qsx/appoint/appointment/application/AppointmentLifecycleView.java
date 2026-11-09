package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.domain.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AppointmentLifecycleView(
        Long id,
        Long userId,
        Long scheduleId,
        AppointmentStatus status,
        LocalDateTime paymentExpiresAt,
        LocalDate appointmentDate,
        LocalTime startTime,
        Integer visitNumber) {
}

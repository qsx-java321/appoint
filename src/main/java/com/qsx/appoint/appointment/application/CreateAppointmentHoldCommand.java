package com.qsx.appoint.appointment.application;

import java.time.LocalDateTime;

public record CreateAppointmentHoldCommand(
        Long userId,
        Long scheduleId,
        Long patientId,
        LocalDateTime createdAt) {
}


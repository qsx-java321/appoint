package com.qsx.appoint.appointment.application;

import java.time.LocalDateTime;

public record CreateAppointmentHoldCommand(
        Long userId,
        Long doctorId,
        java.time.LocalDate appointmentDate,
        String sessionType,
        Long patientId,
        LocalDateTime createdAt) {
}


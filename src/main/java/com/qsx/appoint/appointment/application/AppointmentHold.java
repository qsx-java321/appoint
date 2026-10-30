package com.qsx.appoint.appointment.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AppointmentHold(
        Long appointmentOrderId,
        String appointmentNo,
        Long userId,
        String status,
        BigDecimal amount,
        String currency,
        LocalDateTime paymentExpiresAt,
        LocalDateTime createdAt) {
}


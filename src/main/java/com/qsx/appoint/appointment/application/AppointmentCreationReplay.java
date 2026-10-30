package com.qsx.appoint.appointment.application;

public record AppointmentCreationReplay(
        String requestHash,
        Long appointmentOrderId,
        String appointmentNo,
        boolean completed) {
}

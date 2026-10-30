package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.AppointmentCreationResult;

public interface BookingCoordinator {
    AppointmentCreationResult create(Long userId, String idempotencyKey, CreateAppointmentRequest request);
}


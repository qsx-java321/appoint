package com.qsx.appoint.appointment.application;

import java.time.LocalDateTime;

public interface AppointmentCreationRequestService {
    Long register(Long userId, String idempotencyKey, String requestHash, LocalDateTime createdAt);
    void complete(Long requestId, Long appointmentOrderId, String responseJson);
    AppointmentCreationReplay find(Long userId, String idempotencyKey);
}


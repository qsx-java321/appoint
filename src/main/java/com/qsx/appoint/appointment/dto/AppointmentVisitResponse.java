package com.qsx.appoint.appointment.dto;

import java.time.LocalDateTime;

public record AppointmentVisitResponse(String status, LocalDateTime checkedInAt, LocalDateTime startedAt,
                                       LocalDateTime completedAt, boolean canCheckIn,
                                       LocalDateTime checkInOpensAt, LocalDateTime checkInClosesAt) {}

package com.qsx.appoint.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
@Getter
@AllArgsConstructor
public class ScheduleResponse {
    private final Long id;
    private final Long doctorId;
    private final String doctorNo;
    private final LocalDate appointmentDate;
    private final String sessionType;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String status;
    private final Integer totalCapacity;
    private final Integer availableCapacity;
    private final String availabilityStatus;
    private final boolean bookable;
    private final LocalDateTime releaseAt;
    private final LocalDateTime bookingCutoffAt;
}

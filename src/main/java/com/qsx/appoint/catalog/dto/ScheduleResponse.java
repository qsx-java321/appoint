package com.qsx.appoint.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.qsx.appoint.catalog.model.ScheduleAvailability;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
@Getter
@AllArgsConstructor
public class ScheduleResponse {
    @JsonIgnore
    private final Long id;
    private final Long doctorId;
    private final String doctorNo;
    private final LocalDate appointmentDate;
    private final String sessionType;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final Integer totalCapacity;
    private final Integer availableCapacity;
    @JsonSerialize(using = ToStringSerializer.class)
    private final BigDecimal registrationFee;
    private final String feeCurrency;
    private final boolean bookable;
    private final LocalDateTime bookingClosesAt;
    private final ScheduleAvailability availability;
    private final LocalDateTime serverTime;
}

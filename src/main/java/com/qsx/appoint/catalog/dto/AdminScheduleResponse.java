package com.qsx.appoint.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AdminScheduleResponse {
    private final Long id;
    private final Long doctorId;
    private final LocalDate appointmentDate;
    private final String sessionType;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String status;
    private final Integer totalCapacity;
    private final Integer availableCapacity;
    private final Integer heldCapacity;
    private final Integer confirmedCapacity;
    private final Integer nextVisitNumber;
    @JsonSerialize(using = ToStringSerializer.class)
    private final BigDecimal registrationFee;
    private final String feeCurrency;
    private final LocalDateTime closedAt;
    private final Long closedBy;
}

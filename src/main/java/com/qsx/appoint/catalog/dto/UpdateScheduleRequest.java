package com.qsx.appoint.catalog.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.math.BigDecimal;

@Getter
@Setter
public class UpdateScheduleRequest {
    private Long doctorId;
    private LocalDate appointmentDate;
    private String sessionType;
    private BigDecimal registrationFee;
    private String feeCurrency;
}

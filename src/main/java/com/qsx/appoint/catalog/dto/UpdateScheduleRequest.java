package com.qsx.appoint.catalog.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateScheduleRequest {
    private Long doctorId;
    private LocalDate appointmentDate;
    private String sessionType;
}

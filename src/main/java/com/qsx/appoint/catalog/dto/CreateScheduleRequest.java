package com.qsx.appoint.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateScheduleRequest {
    @NotNull
    private Long doctorId;
    @NotNull
    private LocalDate appointmentDate;
    @NotBlank
    private String sessionType;
    @NotNull
    @Min(1)
    private Integer totalCapacity;
}

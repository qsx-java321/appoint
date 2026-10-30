package com.qsx.appoint.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.math.BigDecimal;

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
    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal registrationFee;
    @NotBlank
    @Pattern(regexp = "CNY")
    private String feeCurrency;
}

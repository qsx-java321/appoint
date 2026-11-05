package com.qsx.appoint.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateBookingSettingsRequest(
        @NotNull @Min(1) Integer capacityPerSession,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal registrationFee) {
}

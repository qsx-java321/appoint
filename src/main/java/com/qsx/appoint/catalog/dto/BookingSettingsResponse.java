package com.qsx.appoint.catalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingSettingsResponse(
        Integer capacityPerSession,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal registrationFee,
        String currency,
        LocalDateTime updatedAt) {
}

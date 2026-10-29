package com.qsx.appoint.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdjustScheduleInventoryRequest {
    @NotNull
    private Integer delta;
    @NotBlank
    @Size(max = 200)
    private String reason;
}

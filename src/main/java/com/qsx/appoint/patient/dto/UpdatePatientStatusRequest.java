package com.qsx.appoint.patient.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePatientStatusRequest {
    @NotNull
    private Boolean enabled;
}

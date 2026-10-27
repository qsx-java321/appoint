package com.qsx.appoint.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePatientRequest {
    @NotBlank @Size(max = 64)
    private String name;

    @NotBlank @Size(max = 32)
    private String documentType;

    @NotBlank @Size(max = 32)
    private String documentNo;

    @NotBlank @Size(max = 32)
    private String contactPhone;

    @NotBlank @Size(max = 120)
    private String contactEmail;
}

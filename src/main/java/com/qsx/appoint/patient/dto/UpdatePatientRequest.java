package com.qsx.appoint.patient.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePatientRequest {
    @Size(max = 64)
    private String name;

    @Size(max = 32)
    private String documentType;

    @Size(max = 32)
    private String documentNo;

    @Size(max = 32)
    private String contactPhone;

    @Size(max = 120)
    private String contactEmail;
}

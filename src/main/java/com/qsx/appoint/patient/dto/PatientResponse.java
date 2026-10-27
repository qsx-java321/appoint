package com.qsx.appoint.patient.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PatientResponse {
    private final Long id;
    private final String name;
    private final String documentType;
    private final String documentNoMasked;
    private final String contactPhone;
    private final String contactEmail;
    private final boolean enabled;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}

package com.qsx.appoint.catalog.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDoctorProfileRequest {
    @Size(max = 64)
    private String name;

    @Size(max = 64)
    private String title;

    @Size(max = 500)
    private String introduction;
}

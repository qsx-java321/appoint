package com.qsx.appoint.catalog.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDepartmentRequest {
    @Size(max = 100)
    private String name;
}

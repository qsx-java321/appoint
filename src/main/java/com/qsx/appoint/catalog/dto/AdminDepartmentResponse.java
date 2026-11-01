package com.qsx.appoint.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminDepartmentResponse {
    private final Long id;
    private final Long hospitalId;
    private final String name;
}

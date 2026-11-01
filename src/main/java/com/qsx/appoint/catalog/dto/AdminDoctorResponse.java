package com.qsx.appoint.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminDoctorResponse {
    private final Long id;
    private final String doctorNo;
    private final Long departmentId;
    private final String name;
    private final String title;
    private final String introduction;
}

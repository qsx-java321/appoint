package com.qsx.appoint.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminHospitalResponse {
    private final Long id;
    private final String name;
    private final String address;
}

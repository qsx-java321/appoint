package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预约创建时保存的科室信息。 */
@Getter
@AllArgsConstructor
public class AppointmentDepartmentResponse {
    private final Long id;
    private final String name;
}

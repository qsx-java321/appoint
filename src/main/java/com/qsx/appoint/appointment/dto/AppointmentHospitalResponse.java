package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预约创建时保存的医院信息。 */
@Getter
@AllArgsConstructor
public class AppointmentHospitalResponse {
    private final Long id;
    private final String name;
}

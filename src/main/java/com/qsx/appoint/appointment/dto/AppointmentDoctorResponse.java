package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预约创建时保存的医生及其医院、科室信息。 */
@Getter
@AllArgsConstructor
public class AppointmentDoctorResponse {
    private final Long id;
    private final String doctorNo;
    private final String name;
    private final String title;
    private final AppointmentDepartmentResponse department;
    private final AppointmentHospitalResponse hospital;
}

package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建时医生及所属科室/医院凭证，目录资料变化不改写历史预约。 */
@Getter
@AllArgsConstructor
public class AppointmentDoctorResponse {
    /** 医生ID。 */
    private final Long id;
    /** 创建时医生编号快照。 */
    private final String doctorNo;
    /** 创建时医生姓名快照。 */
    private final String name;
    /** 创建时医生职称快照。 */
    private final String title;
    /** 创建时科室凭证。 */
    private final AppointmentDepartmentResponse department;
    /** 创建时医院凭证。 */
    private final AppointmentHospitalResponse hospital;
}

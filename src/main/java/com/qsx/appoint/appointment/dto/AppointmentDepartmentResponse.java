package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预约凭证中的科室快照。 */
@Getter
@AllArgsConstructor
public class AppointmentDepartmentResponse {
    /** 科室ID。 */
    private final Long id;
    /** 创建时科室名称快照。 */
    private final String name;
}

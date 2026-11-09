package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预约凭证中的医院快照。 */
@Getter
@AllArgsConstructor
public class AppointmentHospitalResponse {
    /** 医院ID。 */
    private final Long id;
    /** 创建时医院名称快照。 */
    private final String name;
    /** 创建时医院地址快照。 */
    private final String address;
}

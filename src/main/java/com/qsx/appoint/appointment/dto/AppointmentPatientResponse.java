package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预约创建时固定的就诊人及联系方式。 */
@Getter
@AllArgsConstructor
public class AppointmentPatientResponse {
    private final Long id;
    private final String name;
    private final String documentType;
    private final String documentNoMasked;
    private final String contactPhone;
    private final String contactEmail;
}

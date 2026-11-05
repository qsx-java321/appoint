package com.qsx.appoint.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 提交预约时明确选择医生、日期、固定场次与当前账号下的就诊人。 */
@Getter @Setter
public class CreateAppointmentRequest {
    @NotNull
    private Long doctorId;

    @NotNull
    private java.time.LocalDate appointmentDate;

    @NotBlank
    private String sessionType;

    @NotNull
    private Long patientId;
}

package com.qsx.appoint.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 提交预约时明确选择号源与当前账号下的就诊人。 */
@Getter @Setter
public class CreateAppointmentRequest {
    @NotNull
    private Long scheduleId;

    @NotNull
    private Long patientId;
}

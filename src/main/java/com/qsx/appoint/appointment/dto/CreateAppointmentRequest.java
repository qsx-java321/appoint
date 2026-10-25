package com.qsx.appoint.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 提交预约时只需要明确选择的号源 ID。 */
@Getter @Setter
public class CreateAppointmentRequest {
    @NotNull
    private Long scheduleId;
}

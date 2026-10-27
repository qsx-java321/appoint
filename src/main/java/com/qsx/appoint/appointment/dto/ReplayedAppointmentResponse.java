package com.qsx.appoint.appointment.dto;

import lombok.Getter;

/** 已成功处理过的创建请求所对应的预约标识。 */
@Getter
public final class ReplayedAppointmentResponse implements AppointmentCreationResult {
    private final Long appointmentOrderId;
    private final String appointmentNo;
    private final boolean replayed = true;

    public ReplayedAppointmentResponse(Long appointmentOrderId, String appointmentNo) {
        this.appointmentOrderId = appointmentOrderId;
        this.appointmentNo = appointmentNo;
    }
}

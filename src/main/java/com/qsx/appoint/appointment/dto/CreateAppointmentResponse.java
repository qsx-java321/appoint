package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 返回预约与支付单的 ID，前端据此进入模拟支付步骤。 */
@Getter @AllArgsConstructor
public class CreateAppointmentResponse {
    private final Long appointmentOrderId;
    private final Long paymentOrderId;
    private final String appointmentStatus;
    private final String paymentStatus;
}

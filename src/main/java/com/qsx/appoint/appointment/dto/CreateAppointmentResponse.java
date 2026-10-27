package com.qsx.appoint.appointment.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import java.time.LocalDateTime;

/** 返回预约与支付单的 ID，前端据此进入模拟支付步骤。 */
@Getter
public final class CreateAppointmentResponse implements AppointmentCreationResult {
    private final Long appointmentOrderId;
    private final Long paymentOrderId;
    private final String appointmentStatus;
    private final String paymentStatus;
    private final LocalDateTime paymentExpiresAt;
    private final LocalDateTime serverTime;

    @JsonCreator
    public CreateAppointmentResponse(
            @JsonProperty("appointmentOrderId") Long appointmentOrderId,
            @JsonProperty("paymentOrderId") Long paymentOrderId,
            @JsonProperty("appointmentStatus") String appointmentStatus,
            @JsonProperty("paymentStatus") String paymentStatus,
            @JsonProperty("paymentExpiresAt") LocalDateTime paymentExpiresAt,
            @JsonProperty("serverTime") LocalDateTime serverTime) {
        this.appointmentOrderId = appointmentOrderId;
        this.paymentOrderId = paymentOrderId;
        this.appointmentStatus = appointmentStatus;
        this.paymentStatus = paymentStatus;
        this.paymentExpiresAt = paymentExpiresAt;
        this.serverTime = serverTime;
    }
}

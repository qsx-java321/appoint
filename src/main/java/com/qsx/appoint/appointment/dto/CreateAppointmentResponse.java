package com.qsx.appoint.appointment.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/** 返回预约与支付单的 ID，前端据此进入模拟支付步骤。 */
@Getter
public final class CreateAppointmentResponse implements AppointmentCreationResult {
    private final Long appointmentOrderId;
    private final Long paymentOrderId;
    private final String appointmentStatus;
    private final String paymentStatus;
    @JsonSerialize(using = ToStringSerializer.class)
    private final BigDecimal amount;
    private final String currency;
    private final LocalDateTime paymentExpiresAt;
    private final LocalDateTime serverTime;

    @JsonCreator
    public CreateAppointmentResponse(
            @JsonProperty("appointmentOrderId") Long appointmentOrderId,
            @JsonProperty("paymentOrderId") Long paymentOrderId,
            @JsonProperty("appointmentStatus") String appointmentStatus,
            @JsonProperty("paymentStatus") String paymentStatus,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("paymentExpiresAt") LocalDateTime paymentExpiresAt,
            @JsonProperty("serverTime") LocalDateTime serverTime) {
        this.appointmentOrderId = appointmentOrderId;
        this.paymentOrderId = paymentOrderId;
        this.appointmentStatus = appointmentStatus;
        this.paymentStatus = paymentStatus;
        this.amount = amount;
        this.currency = currency;
        this.paymentExpiresAt = paymentExpiresAt;
        this.serverTime = serverTime;
    }
}

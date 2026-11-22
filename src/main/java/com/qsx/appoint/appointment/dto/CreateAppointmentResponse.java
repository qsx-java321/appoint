package com.qsx.appoint.appointment.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 预约和支付单的创建回执，首次成功后保存，供同一请求重放。
 * 实时状态和倒计时使用预约详情。
 */
@Getter
public final class CreateAppointmentResponse implements AppointmentCreationResult {
    /** 关联预约单ID，区别于支付单ID。 */
    private final Long appointmentOrderId;
    private final String appointmentNo;
    /** 关联支付单ID，区别于预约单及支付尝试ID。 */
    private final Long paymentOrderId;
    private final String paymentNo;
    /** 首次创建时预约状态，不表示查询时的最新状态。 */
    private final String appointmentStatus;
    /** 支付单稳定状态，不等同预约status或展示workflowStatus。 */
    private final String paymentStatus;
    /** 首次应付金额，单位元；该字段显式序列化为字符串保持HTTP契约。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private final BigDecimal amount;
    /** 金额币种稳定代码，当前新流程使用CNY。 */
    private final String currency;
    private final LocalDateTime createdAt;
    /** 持久化支付截止，截止当秒仍可支付。 */
    private final LocalDateTime paymentExpiresAt;
    /** 创建结果组装时采样的秒精度服务端时间。 */
    private final LocalDateTime serverTime;

    @JsonCreator
    public CreateAppointmentResponse(
            @JsonProperty("appointmentOrderId") Long appointmentOrderId,
            @JsonProperty("appointmentNo") String appointmentNo,
            @JsonProperty("paymentOrderId") Long paymentOrderId,
            @JsonProperty("paymentNo") String paymentNo,
            @JsonProperty("appointmentStatus") String appointmentStatus,
            @JsonProperty("paymentStatus") String paymentStatus,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("createdAt") LocalDateTime createdAt,
            @JsonProperty("paymentExpiresAt") LocalDateTime paymentExpiresAt,
            @JsonProperty("serverTime") LocalDateTime serverTime) {
        this.appointmentOrderId = appointmentOrderId;
        this.appointmentNo = appointmentNo;
        this.paymentOrderId = paymentOrderId;
        this.paymentNo = paymentNo;
        this.appointmentStatus = appointmentStatus;
        this.paymentStatus = paymentStatus;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = createdAt;
        this.paymentExpiresAt = paymentExpiresAt;
        this.serverTime = serverTime;
    }
}

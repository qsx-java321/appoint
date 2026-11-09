package com.qsx.appoint.appointment.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 首次创建HTTP201结果，包含新预约/支付ID与费用/截止快照。
 * <p>创建成功尚未支付；幂等重放改用ReplayedAppointmentResponse，不返回此旧状态冒充实时结果。
 */
@Getter
public final class CreateAppointmentResponse implements AppointmentCreationResult {
    /** 关联预约单ID，区别于支付单ID。 */
    private final Long appointmentOrderId;
    /** 关联支付单ID，区别于预约单及支付尝试ID。 */
    private final Long paymentOrderId;
    /** 首次创建时预约状态，不表示查询时的最新状态。 */
    private final String appointmentStatus;
    /** 支付单稳定状态，不等同预约status或展示workflowStatus。 */
    private final String paymentStatus;
    /** 首次应付金额，单位元；该字段显式序列化为字符串保持HTTP契约。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private final BigDecimal amount;
    /** 金额币种稳定代码，当前新流程使用CNY。 */
    private final String currency;
    /** 持久化支付截止；等于截止即失去及时支付资格，扫描延迟不延长。 */
    private final LocalDateTime paymentExpiresAt;
    /** 本次查询统一的秒精度服务端时间，列表各行共享。 */
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

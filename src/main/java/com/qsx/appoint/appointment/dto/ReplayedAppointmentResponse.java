package com.qsx.appoint.appointment.dto;

import lombok.Getter;

/** 幂等重放HTTP200的预约标识回执，查询详情才获得最新资金和预约状态。 */
@Getter
public final class ReplayedAppointmentResponse implements AppointmentCreationResult {
    /** 关联预约单ID，区别于支付单ID。 */
    private final Long appointmentOrderId;
    /** 预约业务编号，用于凭证识别。 */
    private final String appointmentNo;
    /** 固定为true，表示返回已完成创建的标识回执。 */
    private final boolean replayed = true;

    public ReplayedAppointmentResponse(Long appointmentOrderId, String appointmentNo) {
        this.appointmentOrderId = appointmentOrderId;
        this.appointmentNo = appointmentNo;
    }
}

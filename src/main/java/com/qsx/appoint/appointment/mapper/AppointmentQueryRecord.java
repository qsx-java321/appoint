package com.qsx.appoint.appointment.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 预约查询的内部投影，承载预约单与关联支付单的当前数据。 */
@Getter
@Setter
public class AppointmentQueryRecord {
    private Long id;
    private String appointmentNo;
    private Long appointmentUserId;
    private Long scheduleId;
    private String status;
    private LocalDateTime createdAt;
    private Long paymentOrderId;
    private Long paymentUserId;
    private String paymentStatus;
}

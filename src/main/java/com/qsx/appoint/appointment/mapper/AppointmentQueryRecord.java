package com.qsx.appoint.appointment.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 预约查询的内部投影，承载预约、就诊对象快照与关联支付单数据。 */
@Getter
@Setter
public class AppointmentQueryRecord {
    private Long id;
    private String appointmentNo;
    private Long appointmentUserId;
    private Long scheduleId;
    private Long doctorId;
    private String doctorNo;
    private String doctorName;
    private String doctorTitle;
    private Long departmentId;
    private String departmentName;
    private Long hospitalId;
    private String hospitalName;
    private String status;
    private LocalDateTime createdAt;
    private Long paymentOrderId;
    private Long paymentUserId;
    private String paymentStatus;
}

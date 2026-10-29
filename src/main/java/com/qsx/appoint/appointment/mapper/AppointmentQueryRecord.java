package com.qsx.appoint.appointment.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 预约查询的内部投影，承载预约、就诊对象快照与关联支付单数据。 */
@Getter
@Setter
public class AppointmentQueryRecord {
    private Long id;
    private String appointmentNo;
    private Long appointmentUserId;
    private Long scheduleId;
    private Long patientId;
    private String patientName;
    private String patientDocumentType;
    private String patientDocumentNo;
    private String patientContactPhone;
    private String patientContactEmail;
    private Long doctorId;
    private String doctorNo;
    private String doctorName;
    private String doctorTitle;
    private Long departmentId;
    private String departmentName;
    private Long hospitalId;
    private String hospitalName;
    private String hospitalAddress;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private Integer visitNumber;
    private String scheduleStatus;
    private LocalDateTime createdAt;
    private LocalDateTime paymentExpiresAt;
    private LocalDateTime closedAt;
    private Long paymentOrderId;
    private Long paymentUserId;
    private String paymentStatus;
}

package com.qsx.appoint.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 预约单记录用户对一个号源的预约意图、就诊对象快照及最终确认状态。 */
@Getter @Setter @TableName("appointment_orders")
public class AppointmentOrder {
    private Long id;
    private String appointmentNo;
    private Long userId;
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
    private LocalDateTime paymentExpiresAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

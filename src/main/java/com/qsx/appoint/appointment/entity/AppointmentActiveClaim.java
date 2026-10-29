package com.qsx.appoint.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 同一排班内仍占用库存的就诊人与证件唯一声明。 */
@Getter
@Setter
@TableName("appointment_active_claims")
public class AppointmentActiveClaim {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scheduleId;
    private Long appointmentOrderId;
    private Long patientId;
    private String documentType;
    private String documentNo;
    private LocalDateTime createdAt;
}

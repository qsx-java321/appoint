package com.qsx.appoint.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 预约单记录用户对一个号源的预约意图及最终确认状态。 */
@Getter @Setter @TableName("appointment_orders")
public class AppointmentOrder {
    private Long id;
    private String appointmentNo;
    private Long userId;
    private Long scheduleId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

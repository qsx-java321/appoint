package com.qsx.appoint.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 一次已成功创建预约的幂等请求记录。 */
@Getter
@Setter
@TableName("appointment_create_requests")
public class AppointmentCreateRequest {
    private Long id;
    private Long userId;
    private String idempotencyKey;
    private String requestHash;
    private Long appointmentOrderId;
    private String responseJson;
    private LocalDateTime createdAt;
}

package com.qsx.appoint.appointment.application;

import java.time.LocalDateTime;

/**
 * 协调层交给预约模块的暂占命令；创建时间与最终日期资格分开处理。
 * @param userId 发起账号ID
 * @param doctorId 选定医生ID
 * @param appointmentDate 就诊日期，最终占号时仍须满足D+1至D+7
 * @param sessionType 四场次稳定名称，预约模块负责解析校验
 * @param patientId 该账号下的就诊人ID
 * @param createdAt 外层创建时间，用于凭证和支付截止，不代替最终占号日期
 */
public record CreateAppointmentHoldCommand(
        Long userId,
        Long doctorId,
        java.time.LocalDate appointmentDate,
        String sessionType,
        Long patientId,
        LocalDateTime createdAt) {
}


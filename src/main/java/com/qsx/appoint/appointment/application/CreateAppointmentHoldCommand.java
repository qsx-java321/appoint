package com.qsx.appoint.appointment.application;

/**
 * 协调层交给预约模块的暂占命令；预约模块在锁后准入时采样创建时间。
 * @param userId 发起账号ID
 * @param doctorId 选定医生ID
 * @param appointmentDate 就诊日期，最终占号时须在今天至后天且早于场次下单截止
 * @param sessionType 四场次稳定名称，预约模块负责解析校验
 * @param patientId 该账号下的就诊人ID
 */
public record CreateAppointmentHoldCommand(
        Long userId,
        Long doctorId,
        java.time.LocalDate appointmentDate,
        String sessionType,
        Long patientId) {
}


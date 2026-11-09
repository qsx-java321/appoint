package com.qsx.appoint.appointment.dto;

/**
 * 预约创建结果的封闭类型：首次返回完整建单结果，重放返回预约标识回执。
 * <p>Controller按具体结果分别返回201/200，调用方不能假定两者字段相同。
 */
public sealed interface AppointmentCreationResult
        permits CreateAppointmentResponse, ReplayedAppointmentResponse {
}

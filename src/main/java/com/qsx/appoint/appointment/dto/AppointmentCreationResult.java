package com.qsx.appoint.appointment.dto;

/** 创建预约的首次成功结果或幂等重试回执。 */
public sealed interface AppointmentCreationResult
        permits CreateAppointmentResponse, ReplayedAppointmentResponse {
}

package com.qsx.appoint.appointment.dto;

/** 首次成功、成功重放和仍在提交分别对应 HTTP 201、200 和 202。 */
public sealed interface AppointmentCreationResult
        permits CreateAppointmentResponse, ReplayedAppointmentResponse, PendingAppointmentResponse {
}

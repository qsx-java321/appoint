package com.qsx.appoint.appointment.application;

/** 已登记请求的摘要和创建时保存的完整回执。 */
public record AppointmentCreationReplay(String requestHash, String responseJson) {
}

package com.qsx.appoint.appointment.dto;

/** 同一提交仍可能在创建事务内，客户端继续使用原令牌和请求体。 */
public record PendingAppointmentResponse(boolean pending) implements AppointmentCreationResult {}

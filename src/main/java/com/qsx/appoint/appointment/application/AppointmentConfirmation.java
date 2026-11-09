package com.qsx.appoint.appointment.application;

/**
 * 预约完整确认操作的结果，序号由预约模块在场次锁内分配。
 * @param appointmentOrderId 已确认预约单ID
 * @param visitNumber 场次内分配的就诊序号；取消后不复用，不表示精确就诊时间
 */
public record AppointmentConfirmation(Long appointmentOrderId, Integer visitNumber) { }

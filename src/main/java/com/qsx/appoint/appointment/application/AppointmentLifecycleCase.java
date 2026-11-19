package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.domain.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约侧稳定业务事实；只有当前事务lock返回的原对象可用于写入。
 * <p>不携带库存或下一序号；record值相等不等于持有同一事务写授权。
 * @param id 预约单主键
 * @param userId 账号用户ID
 * @param scheduleId 内部场次关联ID；调用方不据此直接更新库存
 * @param status 显式从存储字符串映射的预约状态
 * @param paymentExpiresAt 最晚支付时刻，包含当秒
 * @param appointmentDate 凭证就诊日期
 * @param startTime 凭证开诊时刻，用于到院规则
 * @param doctorId 预约快照的医生ID，用于就诊归属校验
 * @param visitNumber 已分配的就诊序号，未确认时为null
 * @param endTime 凭证结束时刻，用于签到截止判断
 */
public record AppointmentLifecycleCase(
        Long id,
        Long userId,
        Long scheduleId,
        AppointmentStatus status,
        LocalDateTime paymentExpiresAt,
        LocalDate appointmentDate,
        LocalTime startTime,
        Integer visitNumber,
        Long doctorId,
        LocalTime endTime) {
}

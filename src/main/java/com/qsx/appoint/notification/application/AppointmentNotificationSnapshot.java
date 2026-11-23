package com.qsx.appoint.notification.application;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 预约单上已保存的通知快照，只读且不随就诊人或医疗目录后续修改变化。
 * @param appointmentOrderId 预约单主键
 * @param userId 下单账号ID，通知唯一接收人
 * @param patientName 预约时的就诊人姓名
 * @param hospitalName 预约时的医院名称
 * @param departmentName 预约时的科室名称
 * @param doctorName 预约时的医生姓名
 * @param appointmentDate 就诊日期
 * @param startTime 场次开始时间
 * @param endTime 场次结束时间
 * @param visitNumber 确认时分配的就诊序号
 */
public record AppointmentNotificationSnapshot(
        Long appointmentOrderId,
        Long userId,
        String patientName,
        String hospitalName,
        String departmentName,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        Integer visitNumber) {
}

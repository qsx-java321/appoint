package com.qsx.appoint.appointment.application;

/**
 * 幂等冲突后的重放事实，不携带最新支付状态。
 * @param requestHash 原请求的规范化内容摘要
 * @param appointmentOrderId 关联预约ID，未完成登记可为null
 * @param appointmentNo 已存在预约的业务编号，关联预约缺失可为null
 * @param completed 回执已保存且关联预约存在才为true
 */
public record AppointmentCreationReplay(
        String requestHash,
        Long appointmentOrderId,
        String appointmentNo,
        boolean completed) {
}

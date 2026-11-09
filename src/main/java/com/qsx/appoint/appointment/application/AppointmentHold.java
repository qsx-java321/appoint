package com.qsx.appoint.appointment.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预约暂占后的稳定结果，供协调层在同事务内创建支付单，不暴露Entity。
 * @param appointmentOrderId 新预约单ID
 * @param appointmentNo 预约业务编号
 * @param userId 账号用户ID
 * @param status 暂占结果的预约状态字符串
 * @param amount 场次费用快照，单位元
 * @param currency 费用币种，当前CNY
 * @param paymentExpiresAt 按外层创建时间计算的支付截止，必须严格早于才可及时支付
 * @param createdAt 外层创建事务采样的服务端时间
 */
public record AppointmentHold(
        Long appointmentOrderId,
        String appointmentNo,
        Long userId,
        String status,
        BigDecimal amount,
        String currency,
        LocalDateTime paymentExpiresAt,
        LocalDateTime createdAt) {
}


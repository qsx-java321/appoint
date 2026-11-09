package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;

/**
 * 支付侧稳定事实；find结果只读，写入必须使用本事务lock返回的原对象。
 * @param id 支付单主键
 * @param paymentNo 支付业务编号
 * @param appointmentOrderId 唯一关联的预约ID
 * @param userId 支付所属账号ID，协调层校验与预约一致
 * @param status 显式从存储字符串转换的支付状态
 * @param paidAt 本系统确认收款时间；尚未收款或历史缺失时可为null
 */
public record PaymentLifecycleCase(
        Long id,
        String paymentNo,
        Long appointmentOrderId,
        Long userId,
        PaymentOrderStatus status,
        java.time.LocalDateTime paidAt) {
}

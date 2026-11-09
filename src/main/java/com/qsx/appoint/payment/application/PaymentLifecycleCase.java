package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
/** 稳定业务事实；写入只接受当前事务lock返回的原对象，不能使用find或跨事务副本。 */
public record PaymentLifecycleCase(
        Long id,
        String paymentNo,
        Long appointmentOrderId,
        Long userId,
        PaymentOrderStatus status,
        java.time.LocalDateTime paidAt) {
}

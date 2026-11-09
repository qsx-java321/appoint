package com.qsx.appoint.coordination.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentSucceeded v1的稳定收款事实，事务Processor据此确认预约或补偿退款。
 * @param eventId Inbox幂等UUID，重投/重放不得改写
 * @param eventVersion 当前只接受1
 * @param occurredAt 本系统产生成功事件的时间
 * @param paymentOrderId 资金事实所属支付ID
 * @param appointmentOrderId 应确认/补偿的预约ID
 * @param paymentAttemptId 成功支付尝试ID
 * @param amount 已确认金额，正数，单位元
 * @param currency 币种，目前CNY
 * @param channel 渠道稳定名称
 * @param paidAt 本系统确认收款的服务端时间，严格早于截止才可确认
 */
public record PaymentSucceededEvent(
        String eventId,
        int eventVersion,
        LocalDateTime occurredAt,
        Long paymentOrderId,
        Long appointmentOrderId,
        Long paymentAttemptId,
        BigDecimal amount,
        String currency,
        String channel,
        LocalDateTime paidAt) {
}

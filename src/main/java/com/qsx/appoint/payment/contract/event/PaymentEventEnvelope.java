package com.qsx.appoint.payment.contract.event;

import java.time.LocalDateTime;

/**
 * PaymentEventEnvelope 的稳定v1消息结构，沿用生产Long文本和时间配置。
 * @param eventId 一次业务事件的UUID，重发和重放保持不变
 * @param eventType 稳定事件类型名，不包含Java类名
 * @param eventVersion 当前wire版本1
 * @param aggregateId 支付单ID，不是预约ID
 * @param occurredAt 本系统记录该事实的业务时间
 * @param payload 对应事件载荷，不能使用持久化Entity
 */
public record PaymentEventEnvelope<T>(String eventId, String eventType, int eventVersion, Long aggregateId, LocalDateTime occurredAt, T payload) {}

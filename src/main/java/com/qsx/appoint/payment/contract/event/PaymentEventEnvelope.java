package com.qsx.appoint.payment.contract.event;

import java.time.LocalDateTime;

/** PaymentEventEnvelope 的 v1 消息结构；字段名与历史消息一致，Long 沿用生产序列化配置。 */
public record PaymentEventEnvelope<T>(String eventId, String eventType, int eventVersion, Long aggregateId, LocalDateTime occurredAt, T payload) {}

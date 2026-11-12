package com.qsx.appoint.payment.application;

/** 发布所需的不可变事件快照。payload 已持久化，Adapter 必须原文转发，不重建业务事实。 */
public record OutboxMessage(String eventId, String eventType, int eventVersion, String payload) {}

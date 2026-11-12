package com.qsx.appoint.payment.application;

/** 区分空队列与已转发；eventId 可空，不能为缺失ID的原文伪造新事件。 */
public record ReplayResult(Status status, String eventId) {
    public enum Status { EMPTY, REPLAYED }
}

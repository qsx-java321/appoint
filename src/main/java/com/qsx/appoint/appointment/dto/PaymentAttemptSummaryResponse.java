package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 查询投影中的最近一次支付尝试摘要，不作为支付写模型复用。 */
@Getter
@AllArgsConstructor
public class PaymentAttemptSummaryResponse {
    private final Long id;
    private final String attemptNo;
    private final String channel;
    private final String status;
    private final String providerOrderNo;
    private final String failureCode;
    private final LocalDateTime createdAt;
    private final LocalDateTime completedAt;
}

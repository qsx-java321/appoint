package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 查询投影中的全额退款摘要，不作为支付写模型复用。 */
@Getter
@AllArgsConstructor
public class PaymentRefundSummaryResponse {
    private final Long id;
    private final String refundNo;
    private final BigDecimal amount;
    private final String currency;
    private final String channel;
    private final String status;
    private final String reason;
    private final Integer attemptCount;
    private final String lastFailureCode;
    private final LocalDateTime completedAt;
}

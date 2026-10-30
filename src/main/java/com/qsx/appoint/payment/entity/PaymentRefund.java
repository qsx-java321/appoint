package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付单的原路全额退款；第一阶段每张支付单最多一笔。 */
@Getter @Setter @TableName("payment_refunds")
public class PaymentRefund {
    private Long id;
    private String refundNo;
    private Long paymentOrderId;
    private Long paymentAttemptId;
    private BigDecimal amount;
    private String currency;
    private String channel;
    private String providerRefundNo;
    private String status;
    private String reason;
    private Integer attemptCount;
    private String lastFailureCode;
    private String lastFailureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}


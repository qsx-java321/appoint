package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 一次渠道支付动作；同一支付单可依次产生多次尝试。 */
@Getter @Setter @TableName("payment_attempts")
public class PaymentAttempt {
    private Long id;
    private String attemptNo;
    private Long paymentOrderId;
    private String channel;
    private String providerOrderNo;
    private String providerTransactionNo;
    private String status;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime processingAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}


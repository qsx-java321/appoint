package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/** 支付单只模拟成功路线，用于观察支付与预约状态的一致性。 */
@Getter @Setter @TableName("payment_orders")
public class PaymentOrder {
    private Long id;
    private String paymentNo;
    private Long appointmentOrderId;
    private Long userId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private LocalDateTime refundedAt;
    private Boolean legacy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

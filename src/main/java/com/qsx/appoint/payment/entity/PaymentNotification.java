package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 渠道通知审计，只长期保存标识、校验结果和报文哈希。 */
@Getter @Setter @TableName("payment_notifications")
public class PaymentNotification {
    private Long id;
    private String channel;
    private String notificationId;
    private String providerTransactionNo;
    private String payloadHash;
    private String verificationResult;
    private String processingResult;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
}


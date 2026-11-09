package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 已验签通知的幂等审计，只保存标识、摘要及处理结论，不持久化完整敏感原始报文。 */
@Getter @Setter @TableName("payment_notifications")
public class PaymentNotification {
    /** 本地记录主键。 */
    private Long id;
    /** 渠道稳定名称；枚举值存在不代表该环境已注册Adapter。 */
    private String channel;
    /** 渠道通知ID，与channel共同构成通知幂等范围。 */
    private String notificationId;
    /** 渠道成功收款流水号，未成功或拒绝通知时可为null。 */
    private String providerTransactionNo;
    /** 已验签字段的SHA-256摘要，用于同通知ID异内容检测。 */
    private String payloadHash;
    /** 审计验签结论，当前业务入口只接受已验签通知。 */
    private String verificationResult;
    /** 通知业务处理结论，包含成功、失败、拒绝及忽略结果。 */
    private String processingResult;
    /** 事务写审计时采样的接收记录时间，不是网络最初到达时间。 */
    private LocalDateTime receivedAt;
    /** 本地处理记录时间。 */
    private LocalDateTime processedAt;
}


package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 支付单的一次渠道尝试；同一支付单可以先后多次尝试，但不能有多个活跃尝试。
 * <p>渠道下单与本地T1/T2分段；PROCESSING尝试可在支付单关闭后接收迟到收款。
 */
@Getter @Setter @TableName("payment_attempts")
public class PaymentAttempt {
    /** 本地记录主键。 */
    private Long id;
    /** 本地支付尝试业务编号，用作渠道下单幂等键。 */
    private String attemptNo;
    /** 关联支付单ID，区别于预约单及支付尝试ID。 */
    private Long paymentOrderId;
    /** 渠道稳定名称；枚举值存在不代表该环境已注册Adapter。 */
    private String channel;
    /** 渠道支付订单号，发起前或下单失败可为null。 */
    private String providerOrderNo;
    /** 渠道成功收款流水号，未成功或拒绝通知时可为null。 */
    private String providerTransactionNo;
    /** 尝试状态字符串：CREATED/PROCESSING/SUCCEEDED/FAILED/CLOSED。 */
    private String status;
    /** 支付尝试失败代码，未失败时可为null。 */
    private String failureCode;
    /** 失败摘要，写入时按字段长度截断，不应存储密钥或完整敏感报文。 */
    private String failureMessage;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private LocalDateTime createdAt;
    /** 渠道会话记录为处理中时刻，尚未进入可为null。 */
    private LocalDateTime processingAt;
    /** 本地记录成功或失败结果的完成时间，处理中可为null。 */
    private LocalDateTime completedAt;
    /** 最近一次本地状态或记录更新时间。 */
    private LocalDateTime updatedAt;
}


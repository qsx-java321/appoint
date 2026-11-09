package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单唯一的原路全额退款记录，申请与渠道执行分离。
 * <p>退款失败不恢复预约或占用库存，重试保持同一refundNo和首次原因。
 */
@Getter @Setter @TableName("payment_refunds")
public class PaymentRefund {
    /** 本地记录主键。 */
    private Long id;
    /** 本地退款业务编号，渠道退款以此幂等。 */
    private String refundNo;
    /** 关联支付单ID，区别于预约单及支付尝试ID。 */
    private Long paymentOrderId;
    /** 提供原渠道成功流水的本地支付尝试ID。 */
    private Long paymentAttemptId;
    /** 金额，单位元；与currency一起解释，不按分存储。 */
    private BigDecimal amount;
    /** 金额币种稳定代码，当前新流程使用CNY。 */
    private String currency;
    /** 渠道稳定名称；枚举值存在不代表该环境已注册Adapter。 */
    private String channel;
    /** 渠道退款成功编号，尚未成功时可为null。 */
    private String providerRefundNo;
    /** 退款执行状态PENDING/SUCCEEDED/FAILED；与支付REFUND_PENDING/REFUNDED区分。 */
    private String status;
    /** 首次申请的RefundReason稳定名称，后续幂等申请不覆盖。 */
    private String reason;
    /** 退款本地执行领取计数，区别于监听器消息重试header。 */
    private Integer attemptCount;
    /** 最近退款执行失败代码，无失败可为null。 */
    private String lastFailureCode;
    /** 最近退款执行失败摘要，持久化前截断。 */
    private String lastFailureMessage;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private LocalDateTime createdAt;
    /** 最近一次本地状态或记录更新时间。 */
    private LocalDateTime updatedAt;
    /** 本地记录退款成功的时间，尚未成功可为null。 */
    private LocalDateTime completedAt;
}


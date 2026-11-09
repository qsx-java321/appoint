package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 详情中的全额退款执行摘要，没有退款申请时整个摘要为null。 */
@Getter
@AllArgsConstructor
public class PaymentRefundSummaryResponse {
    /** 本地退款单ID。 */
    private final Long id;
    /** 本地退款业务编号，渠道退款以此幂等。 */
    private final String refundNo;
    /** 金额，单位元；与currency一起解释，不按分存储。 */
    private final BigDecimal amount;
    /** 金额币种稳定代码，当前新流程使用CNY。 */
    private final String currency;
    /** 渠道稳定名称；枚举值存在不代表该环境已注册Adapter。 */
    private final String channel;
    /** 退款执行状态，不用它代替支付状态计算workflow。 */
    private final String status;
    /** 首次退款原因稳定名称。 */
    private final String reason;
    /** 退款本地执行领取计数，区别于监听器消息重试header。 */
    private final Integer attemptCount;
    /** 最近退款执行失败代码，无失败可为null。 */
    private final String lastFailureCode;
    /** 本地记录退款成功的时间，尚未成功可为null。 */
    private final LocalDateTime completedAt;
}

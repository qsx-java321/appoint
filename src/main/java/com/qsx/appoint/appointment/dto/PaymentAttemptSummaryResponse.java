package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** 详情中的最近一次支付尝试摘要，只用于展示，不作为资金写模型。 */
@Getter
@AllArgsConstructor
public class PaymentAttemptSummaryResponse {
    /** 最近一次本地支付尝试ID。 */
    private final Long id;
    /** 本地支付尝试业务编号，用作渠道下单幂等键。 */
    private final String attemptNo;
    /** 渠道稳定名称；枚举值存在不代表该环境已注册Adapter。 */
    private final String channel;
    /** 最近尝试状态，不等同支付单整体状态。 */
    private final String status;
    /** 渠道支付订单号，发起前或下单失败可为null。 */
    private final String providerOrderNo;
    /** 支付尝试失败代码，未失败时可为null。 */
    private final String failureCode;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private final LocalDateTime createdAt;
    /** 本地记录成功或失败结果的完成时间，处理中可为null。 */
    private final LocalDateTime completedAt;
}

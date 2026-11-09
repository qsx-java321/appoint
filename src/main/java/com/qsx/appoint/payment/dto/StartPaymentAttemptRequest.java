package com.qsx.appoint.payment.dto;

import com.qsx.appoint.payment.domain.PaymentChannel;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 用户发起支付尝试的渠道选择请求；非空不等于该渠道在当前环境可用。 */
@Getter @Setter
public class StartPaymentAttemptRequest {
    /** 渠道稳定名称；枚举值存在不代表该环境已注册Adapter。 */
    @NotNull
    private PaymentChannel channel;
}

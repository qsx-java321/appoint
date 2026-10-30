package com.qsx.appoint.coordination.application;

/** dev/test 旧模拟入口在创建真实支付尝试前复用预约侧截止时间和状态规则。 */
public interface LegacyPaymentSimulationPrecheck {
    void validate(Long userId, Long paymentOrderId);
}

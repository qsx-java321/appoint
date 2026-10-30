package com.qsx.appoint.payment.application;

/** 支付模块对协调层暴露的全额原路退款用例。 */
public interface PaymentRefundOperations {
    /** 幂等建立退款事实和 RefundRequested Outbox。 */
    void requestFullRefund(Long paymentOrderId, String reason);
}

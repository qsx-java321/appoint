package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.RefundReason;

/**
 * 全额原路退款申请接口，幂等建立退款单与RefundRequested Outbox。
 * <p>协调层/通知事务调用时加入该事务；也可独立开启本地事务，不同步等待渠道退款。
 */
public interface PaymentRefundOperations {
    /**
     * 幂等确保支付单对应的全额退款请求，不等待渠道退回款项。
     * @param paymentOrderId 已收款的支付单ID
     * @param reason 本次补偿原因；已有退款时沿用原事实，不覆盖原原因或创建新退款
     * @throws com.qsx.appoint.common.BusinessException 支付不存在、无可退款资金或缺少成功渠道流水
     */
    void requestFullRefund(Long paymentOrderId, RefundReason reason);
}

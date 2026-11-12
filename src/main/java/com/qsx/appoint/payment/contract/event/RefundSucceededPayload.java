package com.qsx.appoint.payment.contract.event;


/**
 * RefundSucceededPayload 的稳定v1消息结构，沿用生产Long文本和时间配置。
 * @param refundId 本地退款单ID
 * @param refundNo 本地退款编号，也是渠道退款幂等依据
 * @param paymentOrderId 资金事实所属支付单ID
 * @param providerRefundNo 渠道退款号，失败事件保持null
 * @param failureMessage 失败摘要，成功事件保持null
 */
public record RefundSucceededPayload(Long refundId, String refundNo, Long paymentOrderId, String providerRefundNo, String failureMessage) {}

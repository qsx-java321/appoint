package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.port.VerifiedPaymentNotification;

/**
 * 已验签通知的事务处理契约；调用者为统一Ingress，不是直接面向外部报文的入口。
 * <p>通知审计、资金迁移、成功Outbox和必要迟到退款同事务提交。
 */
public interface PaymentNotificationService {
    /**
     * 在同一事务中处理已验签通知及其幂等审计。
     * @param channel 已完成验签的渠道
     * @param notification Gateway返回的可信字段，不能用未经验证的报文伪造
     * @param payloadHash Ingress基于已验签字段计算的摘要，用于同通知ID内容复验
     * @return 资金处理摘要；重复时duplicate=true，部分业务字段可能为null
     * @throws com.qsx.appoint.common.BusinessException 同ID异摘要、关联不完整或条件迁移冲突
     */
    PaymentNotificationResponse handle(PaymentChannel channel, VerifiedPaymentNotification notification,
                                       String payloadHash);
}

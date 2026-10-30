package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.port.RawPaymentNotification;

/** 渠道通知统一入口：先由 Adapter 验签，再提交支付事实。 */
public interface PaymentNotificationIngress {
    PaymentNotificationResponse handle(PaymentChannel channel, RawPaymentNotification notification);
}

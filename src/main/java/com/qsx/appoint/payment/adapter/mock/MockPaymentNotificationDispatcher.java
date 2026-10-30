package com.qsx.appoint.payment.adapter.mock;

import com.qsx.appoint.payment.application.PaymentNotificationIngress;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 模拟渠道在 HTTP 202 返回后异步投递已签名通知。 */
@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class MockPaymentNotificationDispatcher {
    private final PaymentNotificationIngress ingress;

    @Async
    public void dispatch(RawPaymentNotification notification) {
        ingress.handle(PaymentChannel.MOCK, notification);
    }
}

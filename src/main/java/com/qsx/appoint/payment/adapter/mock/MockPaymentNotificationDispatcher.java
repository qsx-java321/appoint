package com.qsx.appoint.payment.adapter.mock;

import com.qsx.appoint.payment.application.PaymentNotificationIngress;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 仅dev/test的模拟渠道异步通知分发器，代理调用使用@Async。
 * <p>HTTP202与回调执行可能交错，不保证回调一定发生在响应之后；业务仍需轮询最终状态。
 */
@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
public class MockPaymentNotificationDispatcher {
    private final PaymentNotificationIngress ingress;

    /**
     * 通过异步代理将模拟签名通知交给正式Ingress，不直接调用事务写实现。
     * @param notification 模拟渠道已经签名的原始通知
     */
    @Async
    public void dispatch(RawPaymentNotification notification) {
        ingress.handle(PaymentChannel.MOCK, notification);
    }
}

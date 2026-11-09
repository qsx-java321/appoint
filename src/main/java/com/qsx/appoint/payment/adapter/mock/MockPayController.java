package com.qsx.appoint.payment.adapter.mock;

import com.qsx.appoint.common.Result;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅dev/test提供的模拟渠道完成/失败入口，异步投递真实签名通知。
 * <p>响应202不等待业务回调完成；仍经正式Ingress、Outbox和Inbox，不同步确认预约。
 */
@RestController
@RequestMapping("/api/v1/mock-pay/attempts")
@RequiredArgsConstructor
@Profile({"dev", "test"})
public class MockPayController {
    private final MockPaymentGateway gateway;
    private final MockPaymentNotificationDispatcher dispatcher;

    /**
     * 模拟渠道成功并提交异步通知。
     * @param attemptNo 本地支付尝试编号，由checkoutUrl携带
     * @return HTTP202及签名通知；预约确认由正式消息链路继续推进
     */
    @PostMapping("/{attemptNo}/complete")
    public ResponseEntity<Result<RawPaymentNotification>> complete(@PathVariable String attemptNo) {
        RawPaymentNotification notification = gateway.completeAttempt(attemptNo);
        dispatcher.dispatch(notification);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(notification));
    }

    /**
     * 模拟渠道失败并提交异步通知，不直接改业务数据库。
     * @param attemptNo 本地支付尝试编号
     * @return HTTP202及失败签名通知
     */
    @PostMapping("/{attemptNo}/fail")
    public ResponseEntity<Result<RawPaymentNotification>> fail(@PathVariable String attemptNo) {
        RawPaymentNotification notification = gateway.failAttempt(attemptNo);
        dispatcher.dispatch(notification);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(notification));
    }
}

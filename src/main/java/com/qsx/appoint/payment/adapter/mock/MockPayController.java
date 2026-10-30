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

@RestController
@RequestMapping("/api/v1/mock-pay/attempts")
@RequiredArgsConstructor
@Profile({"dev", "test"})
public class MockPayController {
    private final MockPaymentGateway gateway;
    private final MockPaymentNotificationDispatcher dispatcher;

    @PostMapping("/{attemptNo}/complete")
    public ResponseEntity<Result<RawPaymentNotification>> complete(@PathVariable String attemptNo) {
        RawPaymentNotification notification = gateway.completeAttempt(attemptNo);
        dispatcher.dispatch(notification);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(notification));
    }

    @PostMapping("/{attemptNo}/fail")
    public ResponseEntity<Result<RawPaymentNotification>> fail(@PathVariable String attemptNo) {
        RawPaymentNotification notification = gateway.failAttempt(attemptNo);
        dispatcher.dispatch(notification);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(notification));
    }
}

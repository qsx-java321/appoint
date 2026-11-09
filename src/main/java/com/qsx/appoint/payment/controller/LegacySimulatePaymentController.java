package com.qsx.appoint.payment.controller;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.common.Result;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.coordination.application.PaymentSucceededEvent;
import com.qsx.appoint.coordination.application.PaymentSucceededProcessor;
import com.qsx.appoint.coordination.application.LegacyPaymentSimulationPrecheck;
import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.application.PaymentAttemptService;
import com.qsx.appoint.payment.application.PaymentLifecycle;
import com.qsx.appoint.payment.application.PaymentLifecycleCase;
import com.qsx.appoint.payment.application.PaymentNotificationIngress;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.dto.PaymentResponse;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** @deprecated 仅供旧 dev/test 场景过渡；新流程使用支付尝试和 MockPay。 */
@Deprecated(forRemoval = true)
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Profile({"dev", "test"})
public class LegacySimulatePaymentController {
    private final BookingLifecycleCoordinator lifecycleCoordinator;
    private final PaymentLifecycle payments;
    private final PaymentAttemptService attempts;
    private final MockPaymentGateway gateway;
    private final PaymentNotificationIngress notificationIngress;
    private final PaymentSucceededProcessor succeededProcessor;
    private final LegacyPaymentSimulationPrecheck precheck;

    @PostMapping("/{id}/simulate-success")
    public ResponseEntity<Result<PaymentResponse>> simulateSuccess(
            Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        PaymentLifecycleCase payment = payments.find(id);
        if (payment != null && (payment.status() == PaymentOrderStatus.UNPAID)) {
            precheck.validate(userId, id);
            PaymentAttemptResponse attempt = attempts.startAttempt(userId, id, PaymentChannel.MOCK);
            LocalDateTime occurredAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            RawPaymentNotification raw = gateway.complete(attempt.providerOrderNo(),
                    "legacy-notify-" + UUID.randomUUID(), occurredAt);
            PaymentNotificationResponse notification = notificationIngress.handle(PaymentChannel.MOCK, raw);
            succeededProcessor.process(new PaymentSucceededEvent(
                    notification.eventId(), 1, occurredAt, id, payment.appointmentOrderId(),
                    attempt.paymentAttemptId(), raw.amount(), raw.currency(), PaymentChannel.MOCK.name(), occurredAt));
        }
        PaymentResponse response = lifecycleCoordinator.simulatePaymentSuccess(userId, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(response));
    }
}

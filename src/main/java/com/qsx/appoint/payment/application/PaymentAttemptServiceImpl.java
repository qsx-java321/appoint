package com.qsx.appoint.payment.application;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;
import com.qsx.appoint.payment.port.GatewayPaymentRequest;
import com.qsx.appoint.payment.port.GatewayPaymentSession;
import com.qsx.appoint.payment.port.GatewayPaymentStatus;
import com.qsx.appoint.payment.port.PaymentGateway;
import com.qsx.appoint.payment.port.PaymentGatewayRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PaymentAttemptServiceImpl implements PaymentAttemptService {
    private final PaymentAttemptTransaction transaction;
    private final PaymentGatewayRegistry gateways;

    @Override
    public PaymentAttemptResponse startAttempt(Long userId, Long paymentOrderId, PaymentChannel channel) {
        PaymentGateway gateway = gateways.require(channel);
        CreatedPaymentAttempt created = transaction.create(userId, paymentOrderId, channel);
        try {
            GatewayPaymentSession session = gateway.createPayment(new GatewayPaymentRequest(
                    created.paymentNo(), created.attemptNo(), created.amount(), created.currency(), created.expiresAt()));
            if (session.status() != GatewayPaymentStatus.PROCESSING) {
                throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
            }
            transaction.markProcessing(created.attemptId(), session.providerOrderNo());
            return new PaymentAttemptResponse(created.paymentOrderId(), created.attemptId(), created.channel(),
                    "PAYING", "PROCESSING", session.providerOrderNo(), session.checkoutUrl(), created.expiresAt());
        } catch (RuntimeException exception) {
            transaction.markFailed(created.attemptId(), exception.getClass().getSimpleName(), exception.getMessage());
            throw exception;
        }
    }
}

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

/**
 * 支付发起编排：T1登记尝试→事务外渠道下单→T2记录渠道会话或失败。
 * <p>通过独立事务Bean调用确保代理生效；不把慢渠道请求包进数据库事务。
 * @see PaymentAttemptTransaction
 */
@Service
@RequiredArgsConstructor
class PaymentAttemptServiceImpl implements PaymentAttemptService {
    private final PaymentAttemptTransaction transaction;
    private final PaymentGatewayRegistry gateways;
    /** {@inheritDoc} */
    @Override
    public PaymentAttemptResponse startAttempt(Long userId, Long paymentOrderId, PaymentChannel channel) {
        PaymentGateway gateway = gateways.require(channel);
        CreatedPaymentAttempt created = transaction.create(userId, paymentOrderId, channel);
        try {
            // T1已提交，渠道下单在数据库事务之外；即使网络失败也保留一次真实尝试用于审计。
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

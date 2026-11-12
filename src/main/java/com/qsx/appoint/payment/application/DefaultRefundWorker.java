package com.qsx.appoint.payment.application;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.entity.PaymentRefund;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentRefundMapper;
import com.qsx.appoint.payment.port.GatewayRefundRequest;
import com.qsx.appoint.payment.port.GatewayRefundStatus;
import com.qsx.appoint.payment.port.GatewayRefundSubmission;
import com.qsx.appoint.payment.port.PaymentGatewayRegistry;
import com.qsx.appoint.payment.contract.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 退款分段执行器：短事务claim→事务外Gateway.refund→短事务complete或fail。
 * <p>每段都先锁payment再锁refund/attempt；锁后复验，慢失败不得覆盖其他调用已写入的成功。
 * 进程或ACK故障可引起重复渠道请求，渠道须按refundNo幂等。
 */
@Service
@RequiredArgsConstructor
class DefaultRefundWorker implements RefundWorker {
    private final PaymentRefundMapper refundMapper;
    private final PaymentAttemptMapper attemptMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentOutbox paymentOutbox;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final Clock appointmentClock;
    private final TransactionTemplate transactionTemplate;
    /** {@inheritDoc} */
    @Override
    public RefundExecutionResult process(RefundRequestedEvent event) {
        validate(event);
        RefundWork work = transactionTemplate.execute(status -> claim(event));
        if (work == null) return RefundExecutionResult.ALREADY_SUCCEEDED;
        try {
            // claim事务已结束；渠道退款不能持有数据库行锁，重试沿用同一refundNo。
            GatewayRefundSubmission submission = gatewayRegistry.require(PaymentChannel.valueOf(work.channel()))
                    .refund(new GatewayRefundRequest(work.refundNo(), work.providerTransactionNo(),
                            work.amount(), work.currency(), work.reason()));
            if (submission.status() != GatewayRefundStatus.SUCCEEDED) {
                throw new IllegalStateException("渠道未确认退款成功: " + submission.status());
            }
            transactionTemplate.executeWithoutResult(status -> complete(work, submission.providerRefundNo()));
            return RefundExecutionResult.SUCCEEDED;
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> fail(work, exception));
            throw exception;
        }
    }

    /**
     * 领取一次退款执行工作：定位父支付→加锁→复验退款→增加执行计数→读取成功attempt。
     * @return 供事务外调用的工作快照；本地退款已SUCCEEDED返回null避免再次调用渠道
     */
    private RefundWork claim(RefundRequestedEvent event) {
        PaymentRefund lookup = refundMapper.selectById(event.refundId());
        if (lookup == null || !lookup.getRefundNo().equals(event.refundNo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        lockPayment(lookup.getPaymentOrderId());
        PaymentRefund refund = refundMapper.selectByIdForUpdate(event.refundId());
        if (refund == null || !refund.getRefundNo().equals(event.refundNo())
                || !refund.getPaymentOrderId().equals(lookup.getPaymentOrderId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if ("SUCCEEDED".equals(refund.getStatus())) return null;
        if (refundMapper.beginAttempt(refund.getId(), now()) != 1) {
            throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
        }
        PaymentAttempt attempt = attemptMapper.selectByIdForUpdate(refund.getPaymentAttemptId());
        if (attempt == null || !attempt.getPaymentOrderId().equals(refund.getPaymentOrderId())
                || !"SUCCEEDED".equals(attempt.getStatus()) || attempt.getProviderTransactionNo() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new RefundWork(refund.getId(), refund.getRefundNo(), refund.getPaymentOrderId(),
                refund.getAmount(), refund.getCurrency(), refund.getChannel(), refund.getReason(),
                attempt.getProviderTransactionNo());
    }

    /**
     * 渠道确认成功后重新锁payment→refund，原子记录退款成功、资金REFUNDED和结果Outbox。
     * <p>若其他执行已经成功则幂等返回；本段失败不代表渠道未退款，重试必须沿用refundNo。
     */
    private void complete(RefundWork work, String providerRefundNo) {
        LocalDateTime now = now();
        lockPayment(work.paymentOrderId());
        PaymentRefund current = refundMapper.selectByIdForUpdate(work.refundId());
        if (current == null || !current.getPaymentOrderId().equals(work.paymentOrderId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if ("SUCCEEDED".equals(current.getStatus())) return;
        requireOne(refundMapper.markSucceeded(work.refundId(), providerRefundNo, now));
        requireOne(paymentOrderMapper.completeRefund(work.paymentOrderId(), now));
        insertResultEvent("RefundSucceeded", work, providerRefundNo, null, now);
    }

    /**
     * 记录未成功的退款执行失败及RefundFailed事件，随后由process向上传播原异常。
     * <p>锁后发现已经SUCCEEDED立即返回，避免较慢失败覆盖并发成功。
     */
    private void fail(RefundWork work, RuntimeException exception) {
        LocalDateTime now = now();
        lockPayment(work.paymentOrderId());
        PaymentRefund current = refundMapper.selectByIdForUpdate(work.refundId());
        if (current == null) return;
        if (!current.getPaymentOrderId().equals(work.paymentOrderId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if ("SUCCEEDED".equals(current.getStatus())) return;
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (message.length() > 255) message = message.substring(0, 255);
        requireOne(refundMapper.markFailed(work.refundId(), "GATEWAY_ERROR", message, now));
        insertResultEvent("RefundFailed", work, null, message, now);
    }

    /**
     * 在当前结果事务保存RefundSucceeded/RefundFailed v1，保留支付聚合与退款编号。
     * @param providerRefundNo 成功时渠道退款号，失败时可为null
     * @param failureMessage 失败摘要，成功时为null
     */
    private void insertResultEvent(String type, RefundWork work, String providerRefundNo,
                                   String failureMessage, LocalDateTime now) {
        if ("RefundSucceeded".equals(type)) {
            paymentOutbox.recordRefundSucceeded(new RefundSucceededPayload(work.refundId(), work.refundNo(),
                    work.paymentOrderId(), providerRefundNo, null), now);
        } else {
            paymentOutbox.recordRefundFailed(new RefundFailedPayload(work.refundId(), work.refundNo(),
                    work.paymentOrderId(), null, failureMessage), now);
        }
    }
    /** 校验v1、UUID及退款ID/非空编号；永久无效契约在执行渠道前拒绝。 */
    private void validate(RefundRequestedEvent event) {
        try {
            if (event == null || event.eventVersion() != 1 || event.eventId() == null
                    || (event.refundId() == null || event.refundId() <= 0) || event.refundNo() == null || event.refundNo().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            UUID.fromString(event.eventId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
    /** 按父支付ID锁定资金记录，不存在时作为完整性错误阻止子记录写入。 */
    private void lockPayment(Long paymentOrderId) {
        if (paymentOrderMapper.selectByIdForUpdate(paymentOrderId) == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
    /** 状态迁移未命中一行时抛可重试并发错误，避免只保存部分退款结果。 */
    private void requireOne(int count) {
        if (count != 1) throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
    }
    /** 采样本段事务的秒精度服务端时间；不把渠道网络等待包含在数据库锁持有期间。 */
    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private record RefundWork(Long refundId, String refundNo, Long paymentOrderId,
                              java.math.BigDecimal amount, String currency, String channel,
                              String reason, String providerTransactionNo) {
    }
}

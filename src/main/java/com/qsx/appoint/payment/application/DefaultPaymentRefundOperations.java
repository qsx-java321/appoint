package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.entity.PaymentRefund;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentRefundMapper;
import com.qsx.appoint.payment.contract.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 退款申请事务：payment→refund→成功attempt加锁，创建唯一全额退款及请求事件。
 * <p>已有退款直接幂等返回；新退款的资金状态、退款单和Outbox必须一起提交。
 */
@Service
@RequiredArgsConstructor
class DefaultPaymentRefundOperations implements PaymentRefundOperations {
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentAttemptMapper paymentAttemptMapper;
    private final PaymentRefundMapper refundMapper;
    private final PaymentOutbox paymentOutbox;
    private final Clock appointmentClock;
    /** {@inheritDoc} */
    @Override
    @Transactional
    public void requestFullRefund(Long paymentOrderId, RefundReason reason) {
        PaymentOrder payment = paymentOrderMapper.selectByIdForUpdate(paymentOrderId);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        PaymentRefund existing = refundMapper.selectByPaymentOrderIdForUpdate(paymentOrderId);
        // 退款单按payment唯一；已有申请保持原原因与原事件，不能因重投再建一张退款。
        if (existing != null) return;
        if (PaymentOrderStatus.fromStored(payment.getStatus()) != PaymentOrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        PaymentAttempt attempt = paymentAttemptMapper.selectSucceededByPaymentOrderIdForUpdate(paymentOrderId);
        if (attempt == null || attempt.getProviderTransactionNo() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        LocalDateTime now = now();
        if (paymentOrderMapper.requestRefundIfPaid(paymentOrderId) != 1) {
            throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
        }
        PaymentRefund refund = new PaymentRefund();
        refund.setRefundNo("REF" + UUID.randomUUID().toString().replace("-", ""));
        refund.setPaymentOrderId(paymentOrderId);
        refund.setPaymentAttemptId(attempt.getId());
        refund.setAmount(payment.getAmount());
        refund.setCurrency(payment.getCurrency());
        refund.setChannel(attempt.getChannel());
        refund.setStatus("PENDING");
        refund.setReason(reason.name());
        refund.setAttemptCount(0);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        refundMapper.insert(refund);
        insertRequestedEvent(refund, attempt, now);
    }

    /**
     * 保存RefundRequested v1 Outbox，沿用支付聚合ID与已成功attempt的渠道流水。
     * <p>必须与退款单及REFUND_PENDING同事务；序列化失败不能留下无法投递的退款。
     */
    private void insertRequestedEvent(PaymentRefund refund, PaymentAttempt attempt, LocalDateTime now) {
        paymentOutbox.recordRefundRequested(new RefundRequestedPayload(refund.getId(), refund.getRefundNo(),
                refund.getPaymentOrderId(), refund.getPaymentAttemptId(), attempt.getProviderTransactionNo(),
                refund.getAmount(), refund.getCurrency(), refund.getChannel(), refund.getReason()), now);
    }
    /** 采样秒精度服务端时间用于退款申请及请求事件。 */
    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

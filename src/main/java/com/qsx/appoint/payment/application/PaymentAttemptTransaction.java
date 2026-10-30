package com.qsx.appoint.payment.application;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentAttemptStatus;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PaymentAttemptTransaction {
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentAttemptMapper paymentAttemptMapper;
    private final Clock appointmentClock;

    @Transactional
    public CreatedPaymentAttempt create(Long userId, Long paymentOrderId, PaymentChannel channel) {
        PaymentOrder payment = paymentOrderMapper.selectByIdForUpdate(paymentOrderId);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!payment.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        LocalDateTime now = now();
        if (Boolean.TRUE.equals(payment.getLegacy()) || payment.getAmount() == null || payment.getExpiresAt() == null) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        if (!now.isBefore(payment.getExpiresAt())) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        if (!"UNPAID".equals(payment.getStatus())) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        boolean active = paymentAttemptMapper.selectByPaymentOrderIdForUpdate(paymentOrderId).stream()
                .anyMatch(attempt -> PaymentAttemptStatus.CREATED.name().equals(attempt.getStatus())
                        || PaymentAttemptStatus.PROCESSING.name().equals(attempt.getStatus()));
        if (active) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setAttemptNo(UUID.randomUUID().toString());
        attempt.setPaymentOrderId(paymentOrderId);
        attempt.setChannel(channel.name());
        attempt.setStatus(PaymentAttemptStatus.CREATED.name());
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        paymentAttemptMapper.insert(attempt);
        requireOne(paymentOrderMapper.startPayingIfUnpaid(paymentOrderId));
        return new CreatedPaymentAttempt(paymentOrderId, payment.getPaymentNo(), attempt.getId(), attempt.getAttemptNo(),
                channel, payment.getAmount(), payment.getCurrency(), payment.getExpiresAt());
    }

    @Transactional
    public void markProcessing(Long attemptId, String providerOrderNo) {
        PaymentAttempt attempt = requireAttempt(attemptId);
        requireOne(paymentAttemptMapper.markProcessing(attemptId, providerOrderNo, now()));
    }

    @Transactional
    public void markFailed(Long attemptId, String failureCode, String failureMessage) {
        PaymentAttempt attempt = requireAttempt(attemptId);
        requireOne(paymentAttemptMapper.markFailed(attemptId, failureCode, truncate(failureMessage), now()));
        paymentOrderMapper.restoreUnpaidIfPaying(attempt.getPaymentOrderId());
    }

    private PaymentAttempt requireAttempt(Long attemptId) {
        PaymentAttempt attempt = paymentAttemptMapper.selectByIdForUpdate(attemptId);
        if (attempt == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return attempt;
    }

    private void requireOne(int updated) {
        if (updated != 1) throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
    }

    private String truncate(String message) {
        if (message == null) return "渠道调用失败";
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

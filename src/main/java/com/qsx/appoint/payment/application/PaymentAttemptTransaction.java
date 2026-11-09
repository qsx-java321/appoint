package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
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

/**
 * 支付尝试的短本地事务，负责T1创建和T2状态回填。
 * <p>写路径固定payment父锁→attempt子锁，锁后复验；不调用外部Gateway。
 */
@Service
@RequiredArgsConstructor
class PaymentAttemptTransaction {
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentAttemptMapper paymentAttemptMapper;
    private final Clock appointmentClock;

    /**
     * T1：在payment父锁下校验归属、截止、非历史/UNPAID及无活跃尝试，登记CREATED并置PAYING。
     * @param userId 发起账号
     * @param paymentOrderId 目标支付单ID
     * @param channel 已确认可用的渠道
     * @return T1已提交后供事务外Gateway调用的不可变快照
     */
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
        if (PaymentOrderStatus.fromStored(payment.getStatus()) != PaymentOrderStatus.UNPAID) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        boolean active = paymentAttemptMapper.selectByPaymentOrderIdForUpdate(paymentOrderId).stream()
                .anyMatch(attempt -> PaymentAttemptStatus.fromStored(attempt.getStatus()) == PaymentAttemptStatus.CREATED
                        || PaymentAttemptStatus.fromStored(attempt.getStatus()) == PaymentAttemptStatus.PROCESSING);
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

    /**
     * T2成功：payment父锁→attempt子锁后，将CREATED尝试记录为PROCESSING及渠道订单号。
     * @param attemptId T1创建的本地尝试ID
     * @param providerOrderNo 渠道下单返回的订单号
     */
    @Transactional
    public void markProcessing(Long attemptId, String providerOrderNo) {
        PaymentAttempt attempt = requireAttempt(attemptId);
        requireOne(paymentAttemptMapper.markProcessing(attemptId, providerOrderNo, now()));
    }

    /**
     * T2失败：锁后把尚可失败的尝试置FAILED，并仅在支付仍PAYING时恢复UNPAID。
     * <p>不把已由回调推进的PAID或已关闭状态覆盖回未付。
     * @param attemptId T1创建的本地尝试ID
     * @param failureCode 失败类型代码
     * @param failureMessage 渠道异常摘要，持久化前截断到255字符
     */
    @Transactional
    public void markFailed(Long attemptId, String failureCode, String failureMessage) {
        PaymentAttempt attempt = requireAttempt(attemptId);
        requireOne(paymentAttemptMapper.markFailed(attemptId, failureCode, truncate(failureMessage), now()));
        paymentOrderMapper.restoreUnpaidIfPaying(attempt.getPaymentOrderId());
    }

    /**
     * 先只读定位父支付，再按payment→attempt加锁并锁后复验关联。
     * @return 已锁定的尝试；不存在或关联变化抛业务错误
     */
    private PaymentAttempt requireAttempt(Long attemptId) {
        PaymentAttempt lookup = paymentAttemptMapper.selectById(attemptId);
        if (lookup == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (paymentOrderMapper.selectByIdForUpdate(lookup.getPaymentOrderId()) == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        PaymentAttempt attempt = paymentAttemptMapper.selectByIdForUpdate(attemptId);
        if (attempt == null || !attempt.getPaymentOrderId().equals(lookup.getPaymentOrderId())) {
            throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
        }
        return attempt;
    }
    /** 条件更新未恰好命中一行时抛RETRYABLE_CONCURRENCY_ERROR，不把并发覆盖当作成功。 */
    private void requireOne(int updated) {
        if (updated != 1) throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
    }
    /** 将异常文本限制在数据库字段长度内，null使用渠道失败缺省说明。 */
    private String truncate(String message) {
        if (message == null) return "渠道调用失败";
        return message.length() <= 255 ? message : message.substring(0, 255);
    }
    /** 读取秒精度服务端时间；支付截止采用严格早于比较，恰好到期不能发起。 */
    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PaymentLifecycleOperationsImpl implements PaymentLifecycleOperations {
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentAttemptMapper paymentAttemptMapper;
    private final Clock appointmentClock;

    @Override
    public PaymentLifecycleView findPayment(Long id) {
        return view(paymentOrderMapper.selectByIdForLookup(id));
    }

    @Override
    public PaymentLifecycleView lockByAppointment(Long appointmentOrderId) {
        return view(paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointmentOrderId));
    }

    @Override
    public void markPaid(Long id) {
        requireOne(paymentOrderMapper.payIfUnpaid(id));
        // 仅供 dev/test 的旧模拟成功协调路径保留可退款的渠道事实；新流程由真实 attempt 回调写入。
        if (paymentAttemptMapper.selectByPaymentOrderIdForUpdate(id).isEmpty()) {
            LocalDateTime now = LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
            String token = UUID.randomUUID().toString().replace("-", "");
            PaymentAttempt attempt = new PaymentAttempt();
            attempt.setAttemptNo("compat-" + token);
            attempt.setPaymentOrderId(id);
            attempt.setChannel("MOCK");
            attempt.setProviderOrderNo("compat-order-" + token);
            attempt.setProviderTransactionNo("compat-txn-" + token);
            attempt.setStatus("SUCCEEDED");
            attempt.setCreatedAt(now);
            attempt.setProcessingAt(now);
            attempt.setCompletedAt(now);
            attempt.setUpdatedAt(now);
            paymentAttemptMapper.insert(attempt);
        }
    }

    @Override
    public void closeUnpaid(Long id) {
        requireOne(paymentOrderMapper.closeIfUnpaid(id));
    }

    @Override
    public void refundPaid(Long id) {
        requireOne(paymentOrderMapper.refundIfPaid(id));
    }

    private PaymentLifecycleView view(PaymentOrder order) {
        if (order == null) return null;
        return new PaymentLifecycleView(order.getId(), order.getPaymentNo(), order.getAppointmentOrderId(),
                order.getUserId(), PaymentOrderStatus.fromStored(order.getStatus()), order.getPaidAt());
    }

    private void requireOne(int updated) {
        if (updated != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

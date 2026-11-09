package com.qsx.appoint.payment.application;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 支付稳定事实及关闭的数据库实现，身份集合把锁定Case绑定到当前事务。
 * <p>closeUnpaid只关闭UNPAID/PAYING支付单，保留PROCESSING尝试供迟到回调记账退款。
 */
@Service
@RequiredArgsConstructor
class DefaultPaymentLifecycle implements PaymentLifecycle {
    private final PaymentOrderMapper paymentOrderMapper;
    /** {@inheritDoc} */
    @Override
    public PaymentLifecycleCase find(Long id) {
        return view(paymentOrderMapper.selectByIdForLookup(id));
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentLifecycleCase lockByAppointment(Long appointmentOrderId) {
        PaymentLifecycleCase payment = view(paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointmentOrderId));
        if (payment != null) context(true).locked.add(payment);
        return payment;
    }
    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeUnpaid(PaymentLifecycleCase payment) {
        Context context = context(false);
        if (context == null || !context.locked.remove(payment)) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        requireOne(paymentOrderMapper.closeIfUnpaid(payment.id()));
    }

    /**
     * 维护当前事务的Case身份集合；同步器随事务挂起/恢复，事务结束清理。
     * @param create 不存在时是否登记
     * @return 本事务上下文；只读查询且未登记时为null
     */
    private Context context(boolean create) {
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof Context context) return context;
        }
        if (!create) return null;
        Context context = new Context();
        TransactionSynchronizationManager.registerSynchronization(context);
        return context;
    }
    private static final class Context implements TransactionSynchronization {
        final Set<PaymentLifecycleCase> locked = Collections.newSetFromMap(new IdentityHashMap<>());
        @Override public void afterCompletion(int status) { locked.clear(); }
    }
    /** 内部支付Entity映射为类型化业务事实，输入null则返回null。 */
    private PaymentLifecycleCase view(PaymentOrder order) {
        if (order == null) return null;
        return new PaymentLifecycleCase(order.getId(), order.getPaymentNo(), order.getAppointmentOrderId(),
                order.getUserId(), PaymentOrderStatus.fromStored(order.getStatus()), order.getPaidAt());
    }
    /** 关闭条件更新必须命中一行；不符时抛INTERNAL_ERROR回滚外层协调事务。 */
    private void requireOne(int updated) {
        if (updated != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

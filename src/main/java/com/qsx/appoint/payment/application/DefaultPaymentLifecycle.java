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


@Service
@RequiredArgsConstructor
class DefaultPaymentLifecycle implements PaymentLifecycle {
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    public PaymentLifecycleCase find(Long id) {
        return view(paymentOrderMapper.selectByIdForLookup(id));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentLifecycleCase lockByAppointment(Long appointmentOrderId) {
        PaymentLifecycleCase payment = view(paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointmentOrderId));
        if (payment != null) context(true).locked.add(payment);
        return payment;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeUnpaid(PaymentLifecycleCase payment) {
        Context context = context(false);
        if (context == null || !context.locked.remove(payment)) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        requireOne(paymentOrderMapper.closeIfUnpaid(payment.id()));
    }

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

    private PaymentLifecycleCase view(PaymentOrder order) {
        if (order == null) return null;
        return new PaymentLifecycleCase(order.getId(), order.getPaymentNo(), order.getAppointmentOrderId(),
                order.getUserId(), PaymentOrderStatus.fromStored(order.getStatus()), order.getPaidAt());
    }

    private void requireOne(int updated) {
        if (updated != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

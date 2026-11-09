package com.qsx.appoint.coordination.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.PaymentLifecycle;
import com.qsx.appoint.payment.application.PaymentLifecycleCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class DefaultLegacyPaymentSimulationPrecheck implements LegacyPaymentSimulationPrecheck {
    private final AppointmentLifecycle appointments;
    private final PaymentLifecycle payments;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public void validate(Long userId, Long paymentOrderId) {
        PaymentLifecycleCase payment = payments.find(paymentOrderId);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!payment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        AppointmentLifecycleCase appointment = appointments.find(payment.appointmentOrderId());
        if (appointment == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT || payment.status() != PaymentOrderStatus.UNPAID) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        if (!LocalDateTime.now(appointmentClock).isBefore(appointment.paymentExpiresAt())) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
        appointments.lock(appointment.id());
    }
}

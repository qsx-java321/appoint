package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.application.AppointmentLifecycleOperations;
import com.qsx.appoint.appointment.application.AppointmentLifecycleView;
import com.qsx.appoint.appointment.application.ScheduleLifecycleView;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.PaymentLifecycleOperations;
import com.qsx.appoint.payment.application.PaymentLifecycleView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class DefaultLegacyPaymentSimulationPrecheck implements LegacyPaymentSimulationPrecheck {
    private final AppointmentLifecycleOperations appointments;
    private final PaymentLifecycleOperations payments;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public void validate(Long userId, Long paymentOrderId) {
        PaymentLifecycleView payment = payments.findPayment(paymentOrderId);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!payment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        AppointmentLifecycleView appointment = appointments.findAppointment(payment.appointmentOrderId());
        if (appointment == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        if (!"PENDING_PAYMENT".equals(appointment.status()) || !"UNPAID".equals(payment.status())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        if (!LocalDateTime.now(appointmentClock).isBefore(appointment.paymentExpiresAt())) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
        ScheduleLifecycleView schedule = appointments.lockSchedule(appointment.scheduleId());
        if (!"OPEN".equals(schedule.status())) throw new BusinessException(ErrorCode.SCHEDULE_CLOSED);
    }
}

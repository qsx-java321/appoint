package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.application.AppointmentLifecycleOperations;
import com.qsx.appoint.appointment.application.AppointmentLifecycleView;
import com.qsx.appoint.appointment.application.ScheduleLifecycleView;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.persistence.CoordinationInboxEventMapper;
import com.qsx.appoint.payment.application.PaymentRefundOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultPaymentSucceededProcessor implements PaymentSucceededProcessor {
    private static final String CONSUMER = "appointment-payment-succeeded-v1";

    private final CoordinationInboxEventMapper inboxMapper;
    private final AppointmentLifecycleOperations appointments;
    private final PaymentRefundOperations refunds;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public String process(PaymentSucceededEvent event) {
        validate(event);
        if (inboxMapper.insertIgnore(CONSUMER, event.eventId(), "PaymentSucceeded", now()) == 0) {
            return "DUPLICATE";
        }

        AppointmentLifecycleView lookup = appointments.findAppointment(event.appointmentOrderId());
        if (lookup == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        ScheduleLifecycleView schedule = appointments.lockSchedule(lookup.scheduleId());
        AppointmentLifecycleView appointment = appointments.lockAppointment(event.appointmentOrderId());
        if (appointment == null || !appointment.id().equals(event.appointmentOrderId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if ("CONFIRMED".equals(appointment.status())) return "ALREADY_CONFIRMED";
        if (!"PENDING_PAYMENT".equals(appointment.status())) {
            refunds.requestFullRefund(event.paymentOrderId(), "APPOINTMENT_NOT_CONFIRMABLE");
            return "REFUND_REQUIRED";
        }
        if (!event.paidAt().isBefore(appointment.paymentExpiresAt())) {
            appointments.releaseHeldInventory(schedule.id());
            appointments.closePendingAppointment(appointment.id(), "EXPIRED", now());
            refunds.requestFullRefund(event.paymentOrderId(), "APPOINTMENT_NOT_CONFIRMABLE");
            return "REFUND_REQUIRED";
        }

        Integer visitNumber = schedule.nextVisitNumber();
        appointments.confirmInventory(schedule.id(), visitNumber);
        appointments.confirmAppointment(appointment.id(), visitNumber);
        return "CONFIRMED";
    }

    private void validate(PaymentSucceededEvent event) {
        try {
            if (event == null || event.eventVersion() != 1 || event.eventId() == null
                    || event.occurredAt() == null || event.paymentOrderId() == null
                    || event.appointmentOrderId() == null || event.paymentAttemptId() == null
                    || event.amount() == null || event.amount().signum() <= 0
                    || !"CNY".equals(event.currency()) || event.channel() == null
                    || event.paidAt() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            UUID.fromString(event.eventId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

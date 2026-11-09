package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.payment.domain.RefundReason;
import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
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

    private final BookingLifecyclePolicy policy = new BookingLifecyclePolicy();
    private final CoordinationInboxEventMapper inboxMapper;
    private final AppointmentLifecycle appointments;
    private final PaymentRefundOperations refunds;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public String process(PaymentSucceededEvent event) {
        validate(event);
        if (inboxMapper.insertIgnore(CONSUMER, event.eventId(), "PaymentSucceeded", now()) == 0) {
            return "DUPLICATE";
        }

        AppointmentLifecycleCase appointment = appointments.lock(event.appointmentOrderId());
        if (appointment == null || !appointment.id().equals(event.appointmentOrderId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return switch (policy.decidePaymentConfirmation(appointment,event)) {
            case ALREADY_CONFIRMED -> "ALREADY_CONFIRMED";
            case REFUND_CLOSED -> {
                refunds.requestFullRefund(event.paymentOrderId(),RefundReason.APPOINTMENT_NOT_CONFIRMABLE);
                yield "REFUND_REQUIRED";
            }
            case EXPIRE_AND_REFUND -> {
                appointments.closePending(appointment,AppointmentCloseReason.PAYMENT_EXPIRED,now());
                refunds.requestFullRefund(event.paymentOrderId(),RefundReason.APPOINTMENT_NOT_CONFIRMABLE);
                yield "REFUND_REQUIRED";
            }
            case CONFIRM -> {
                appointments.confirm(appointment);
                yield "CONFIRMED";
            }
        };
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

package com.qsx.appoint.coordination.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.application.AppointmentCreationRequestService;
import com.qsx.appoint.appointment.application.AppointmentHold;
import com.qsx.appoint.appointment.application.AppointmentHoldService;
import com.qsx.appoint.appointment.application.CreateAppointmentHoldCommand;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.CreatePaymentOrderCommand;
import com.qsx.appoint.payment.application.PaymentOrderCreationService;
import com.qsx.appoint.payment.application.PaymentOrderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
class BookingCreationTransaction {
    private final AppointmentCreationRequestService creationRequestService;
    private final AppointmentHoldService holdService;
    private final PaymentOrderCreationService paymentOrderCreationService;
    private final Clock appointmentClock;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateAppointmentResponse create(Long userId, String idempotencyKey, String requestHash,
                                             CreateAppointmentRequest request) {
        LocalDateTime now = LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
        Long requestId = creationRequestService.register(userId, idempotencyKey, requestHash, now);
        AppointmentHold hold = holdService.createHold(
                new CreateAppointmentHoldCommand(userId, request.getScheduleId(), request.getPatientId(), now));
        PaymentOrderResult payment = paymentOrderCreationService.createOrder(new CreatePaymentOrderCommand(
                hold.appointmentOrderId(), hold.userId(), hold.amount(), hold.currency(),
                hold.paymentExpiresAt(), hold.createdAt()));
        CreateAppointmentResponse response = new CreateAppointmentResponse(
                hold.appointmentOrderId(), payment.paymentOrderId(), hold.status(), payment.status(),
                payment.amount(), payment.currency(), hold.paymentExpiresAt(), now);
        creationRequestService.complete(requestId, hold.appointmentOrderId(), serialize(response));
        return response;
    }

    private String serialize(CreateAppointmentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}

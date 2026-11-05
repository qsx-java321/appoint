package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.application.AppointmentCreationRequestService;
import com.qsx.appoint.appointment.application.AppointmentCreationReplay;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.ReplayedAppointmentResponse;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultBookingCoordinator implements BookingCoordinator {
    private final AppointmentCreationRequestService creationRequestService;
    private final BookingCreationTransaction creationTransaction;

    @Override
    public AppointmentCreationResult create(Long userId, String rawIdempotencyKey,
                                             CreateAppointmentRequest request) {
        String idempotencyKey = normalizeKey(rawIdempotencyKey);
        String requestHash = hash(request.getDoctorId(), request.getAppointmentDate(),
                request.getSessionType(), request.getPatientId());
        try {
            return creationTransaction.create(userId, idempotencyKey, requestHash, request);
        } catch (DuplicateKeyException exception) {
            AppointmentCreationReplay replay = creationRequestService.find(userId, idempotencyKey);
            if (replay == null) throw exception;
            if (!requestHash.equals(replay.requestHash())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if (!replay.completed() || replay.appointmentOrderId() == null || replay.appointmentNo() == null) {
                throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
            }
            return new ReplayedAppointmentResponse(replay.appointmentOrderId(), replay.appointmentNo());
        }
    }

    private String normalizeKey(String raw) {
        try {
            return UUID.fromString(raw == null ? "" : raw.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String hash(Long doctorId, java.time.LocalDate appointmentDate, String sessionType, Long patientId) {
        try {
            String normalizedSession = sessionType == null ? "" : sessionType.trim().toUpperCase(java.util.Locale.ROOT);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((doctorId + ":" + appointmentDate + ":" + normalizedSession + ":" + patientId)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

}


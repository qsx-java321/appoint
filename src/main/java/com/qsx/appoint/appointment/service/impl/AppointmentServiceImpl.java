package com.qsx.appoint.appointment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.appointment.entity.AppointmentCreateRequest;
import com.qsx.appoint.appointment.mapper.AppointmentQueryRecord;
import com.qsx.appoint.appointment.mapper.AppointmentCreateRequestMapper;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.patient.service.ResidentIdCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 创建预约时先原子占用号源，再创建两个关联订单。
 * 任一写入失败都会回滚，使号源与订单不会出现不一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {
    private final AppointmentCreationTransactionService creationTransactionService;
    private final AppointmentCreateRequestMapper createRequestMapper;
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final AppointmentLifecycleService lifecycleService;
    private final Clock appointmentClock;
    private final ObjectMapper objectMapper;

    @Override
    public CreateAppointmentResponse create(Long userId, String rawIdempotencyKey, CreateAppointmentRequest request) {
        String idempotencyKey = normalizeKey(rawIdempotencyKey);
        String requestHash = hash(request.getScheduleId(), request.getPatientId());
        try {
            return creationTransactionService.create(userId, idempotencyKey, requestHash, request);
        } catch (DuplicateKeyException exception) {
            AppointmentCreateRequest existing = createRequestMapper.selectByUserAndKey(userId, idempotencyKey);
            if (existing == null) throw exception;
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if (existing.getResponseJson() == null || existing.getAppointmentOrderId() == null) {
                throw new BusinessException(ErrorCode.RETRYABLE_CONCURRENCY_ERROR);
            }
            return deserialize(existing.getResponseJson());
        }
    }

    @Override
    public AppointmentResponse getOwned(Long userId, Long orderId) {
        AppointmentQueryRecord record = appointmentOrderMapper.selectQueryById(orderId);
        ensureOwner(userId, record);
        return toResponse(record, now());
    }

    @Override
    public List<AppointmentResponse> listMine(Long userId) {
        LocalDateTime serverTime = now();
        return appointmentOrderMapper.selectQueriesByUserId(userId).stream()
                .map(record -> toResponse(record, serverTime))
                .toList();
    }

    @Override
    public AppointmentResponse cancel(Long userId, Long orderId) {
        lifecycleService.cancelOwned(userId, orderId);
        return getOwned(userId, orderId);
    }

    private void ensureOwner(Long userId, AppointmentQueryRecord record) {
        if (record == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!record.getAppointmentUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private AppointmentResponse toResponse(AppointmentQueryRecord record, LocalDateTime serverTime) {
        ensurePaymentIntegrity(record);
        AppointmentDoctorResponse doctor = new AppointmentDoctorResponse(
                record.getDoctorId(), record.getDoctorNo(), record.getDoctorName(), record.getDoctorTitle(),
                new AppointmentDepartmentResponse(record.getDepartmentId(), record.getDepartmentName()),
                new AppointmentHospitalResponse(record.getHospitalId(), record.getHospitalName(), record.getHospitalAddress()));
        AppointmentPatientResponse patient = new AppointmentPatientResponse(record.getPatientId(), record.getPatientName(),
                record.getPatientDocumentType(), ResidentIdCard.mask(record.getPatientDocumentNo()),
                record.getPatientContactPhone(), record.getPatientContactEmail());
        boolean paymentExpired = "EXPIRED".equals(record.getStatus())
                || ("PENDING_PAYMENT".equals(record.getStatus())
                && !serverTime.isBefore(record.getPaymentExpiresAt()));
        boolean pendingAndUnpaid = "PENDING_PAYMENT".equals(record.getStatus())
                && "UNPAID".equals(record.getPaymentStatus());
        return new AppointmentResponse(record.getId(), record.getAppointmentNo(), record.getScheduleId(),
                record.getAppointmentDate(), record.getStartTime(), record.getEndTime(), record.getStatus(),
                record.getCreatedAt(), record.getPaymentOrderId(), record.getPaymentStatus(),
                record.getPaymentExpiresAt(), record.getClosedAt(), serverTime, paymentExpired,
                pendingAndUnpaid && !paymentExpired, pendingAndUnpaid, patient, doctor);
    }

    private void ensurePaymentIntegrity(AppointmentQueryRecord record) {
        if (record.getPaymentOrderId() == null) {
            log.error("预约查询发现缺失支付单，appointmentOrderId={}", record.getId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (!record.getAppointmentUserId().equals(record.getPaymentUserId())) {
            log.error("预约查询发现支付单归属不一致，appointmentOrderId={}, paymentOrderId={}",
                    record.getId(), record.getPaymentOrderId());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private String normalizeKey(String raw) {
        try {
            return UUID.fromString(raw == null ? "" : raw.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String hash(Long scheduleId, Long patientId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((scheduleId + ":" + patientId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private CreateAppointmentResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, CreateAppointmentResponse.class);
        } catch (JsonProcessingException exception) {
            log.error("预约幂等响应无法解析", exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}

package com.qsx.appoint.coordination.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.application.AppointmentCreationRequestService;
import com.qsx.appoint.appointment.application.AppointmentCreationReplay;
import com.qsx.appoint.appointment.dto.BookingTokenResponse;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.dto.PendingAppointmentResponse;
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

/**
 * 预约创建的事务外入口。已提交的回执优先，首次下单消费令牌后进入创建事务。
 * @see BookingCreationTransaction
 */
@Service
@RequiredArgsConstructor
class DefaultBookingCoordinator implements BookingCoordinator {
    private final AppointmentCreationRequestService creationRequestService;
    private final BookingCreationTransaction creationTransaction;
    private final BookingTokenStore tokens;
    private final ObjectMapper objectMapper;

    @Override
    public BookingTokenResponse issueToken(Long userId) {
        return tokens.issue(userId);
    }

    /**
     * {@inheritDoc}
     * <p>此入口不持有创建事务；DuplicateKeyException的检查和重放发生在事务Bean返回异常后。
     */
    @Override
    public AppointmentCreationResult create(Long userId, String rawIdempotencyKey,
                                             CreateAppointmentRequest request) {
        String idempotencyKey = normalizeKey(rawIdempotencyKey);
        String requestHash = hash(request.getDoctorId(), request.getAppointmentDate(),
                request.getSessionType(), request.getPatientId());
        var replay = findReplay(userId, idempotencyKey, requestHash);
        if (replay != null) return replay;

        String appointmentNo = "AP" + UUID.randomUUID().toString().replace("-", "");
        var claim = tokens.claim(userId, idempotencyKey, requestHash, appointmentNo);
        if (claim == BookingTokenStore.Claim.CONFLICT) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (claim != BookingTokenStore.Claim.CLAIMED) {
            // 首次请求可能已在前一次读回执与 Redis 校验之间提交。
            replay = findReplay(userId, idempotencyKey, requestHash);
            if (replay != null) return replay;
            if (claim == BookingTokenStore.Claim.PENDING) return new PendingAppointmentResponse(true);
            throw new BusinessException(ErrorCode.BOOKING_TOKEN_INVALID);
        }

        try {
            return creationTransaction.create(userId, idempotencyKey, requestHash, appointmentNo, request);
        } catch (DuplicateKeyException exception) {
            replay = findReplay(userId, idempotencyKey, requestHash);
            if (replay != null) return replay;
            throw exception;
        } catch (BusinessException exception) {
            if (exception.getErrorCode().getHttpStatus().is4xxClientError()) tokens.discardBinding(idempotencyKey);
            throw exception;
        }
    }

    private AppointmentCreationResult findReplay(Long userId, String idempotencyKey, String requestHash) {
        AppointmentCreationReplay replay = creationRequestService.find(userId, idempotencyKey);
        if (replay == null) return null;
        if (!requestHash.equals(replay.requestHash())) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (replay.responseJson() == null) return new PendingAppointmentResponse(true);
        try {
            return new ReplayedAppointmentResponse(objectMapper.readValue(replay.responseJson(), CreateAppointmentResponse.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取已保存的预约创建回执", exception);
        }
    }

    /**
     * 把请求键解析为标准UUID文本，消除大小写与首尾空白差异。
     * @param raw 请求头原值；null或无法解析的值均拒绝
     * @return 规范化UUID字符串
     * @throws com.qsx.appoint.common.BusinessException 无效键，错误码VALIDATION_ERROR
     */
    private String normalizeKey(String raw) {
        try {
            String value = raw == null ? "" : raw.trim();
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /**
     * 对医生、日期、规范化场次类型和就诊人生成SHA-256摘要。
     * <p>摘要只比较预约意图，不包含易变目录名称或当前库存，保证网络重试可识别同一请求。
     * @return 十六进制摘要；不作为认证签名使用
     */
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


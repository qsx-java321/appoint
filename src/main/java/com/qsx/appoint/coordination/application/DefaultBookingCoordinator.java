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

/**
 * 预约创建的事务外入口：规范化幂等键/内容摘要，委托独立Spring事务Bean创建。
 * <p>唯一键冲突必须等创建事务回滚后再查询重放，避免在已标记回滚的事务中继续返回成功。
 * 同键不同摘要拒绝；登记尚未完整完成返回可重试错误，不再次占号。
 * @see BookingCreationTransaction
 */
@Service
@RequiredArgsConstructor
class DefaultBookingCoordinator implements BookingCoordinator {
    private final AppointmentCreationRequestService creationRequestService;
    private final BookingCreationTransaction creationTransaction;

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
        try {
            return creationTransaction.create(userId, idempotencyKey, requestHash, request);
        } catch (DuplicateKeyException exception) {
            // 异常已穿过事务代理并完成回滚；此时才能读取其他请求已提交的幂等登记。
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

    /**
     * 把请求键解析为标准UUID文本，消除大小写与首尾空白差异。
     * @param raw 请求头原值；null或无法解析的值均拒绝
     * @return 规范化UUID字符串
     * @throws com.qsx.appoint.common.BusinessException 无效键，错误码VALIDATION_ERROR
     */
    private String normalizeKey(String raw) {
        try {
            return UUID.fromString(raw == null ? "" : raw.trim()).toString();
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


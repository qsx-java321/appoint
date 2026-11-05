package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.bookingquery.model.WorkflowStatus;
import com.qsx.appoint.bookingquery.persistence.BookingQueryMapper;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.patient.service.ResidentIdCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 预约详情/列表的跨模块只读实现；不参与任何写事务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultBookingQueryService implements AppointmentService {
    private final BookingQueryMapper mapper;
    private final Clock appointmentClock;

    @Override
    public AppointmentResponse getOwned(Long userId, Long orderId) {
        BookingQueryRecord record = mapper.selectById(orderId);
        ensureOwner(userId, record);
        return toResponse(record, now());
    }

    @Override
    public List<AppointmentResponse> listMine(Long userId) {
        LocalDateTime serverTime = now();
        return mapper.selectByUserId(userId).stream()
                .map(record -> toResponse(record, serverTime))
                .toList();
    }

    private void ensureOwner(Long userId, BookingQueryRecord record) {
        if (record == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!record.getAppointmentUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private AppointmentResponse toResponse(BookingQueryRecord record, LocalDateTime serverTime) {
        ensurePaymentIntegrity(record);
        AppointmentDoctorResponse doctor = new AppointmentDoctorResponse(
                record.getDoctorId(), record.getDoctorNo(), record.getDoctorName(), record.getDoctorTitle(),
                new AppointmentDepartmentResponse(record.getDepartmentId(), record.getDepartmentName()),
                new AppointmentHospitalResponse(record.getHospitalId(), record.getHospitalName(),
                        record.getHospitalAddress()));
        AppointmentPatientResponse patient = new AppointmentPatientResponse(record.getPatientId(),
                record.getPatientName(), record.getPatientDocumentType(),
                ResidentIdCard.mask(record.getPatientDocumentNo()), record.getPatientContactPhone(),
                record.getPatientContactEmail());
        boolean paymentExpired = "EXPIRED".equals(record.getStatus())
                || ("PENDING_PAYMENT".equals(record.getStatus())
                && record.getPaymentExpiresAt() != null
                && !serverTime.isBefore(record.getPaymentExpiresAt()));
        boolean pendingAndUnpaid = "PENDING_PAYMENT".equals(record.getStatus())
                && "UNPAID".equals(record.getPaymentStatus());
        boolean pendingAndCancellable = "PENDING_PAYMENT".equals(record.getStatus())
                && ("UNPAID".equals(record.getPaymentStatus()) || "PAYING".equals(record.getPaymentStatus()));
        boolean confirmedAndPaid = "CONFIRMED".equals(record.getStatus())
                && "PAID".equals(record.getPaymentStatus());
        WorkflowStatus workflow = workflowStatus(record);
        LocalDateTime cancellationDeadline = LocalDateTime.of(
                record.getAppointmentDate(), record.getStartTime()).minusHours(24);
        PaymentAttemptSummaryResponse attempt = record.getAttemptId() == null ? null
                : new PaymentAttemptSummaryResponse(record.getAttemptId(), record.getAttemptNo(),
                record.getAttemptChannel(), record.getAttemptStatus(), record.getProviderOrderNo(),
                record.getAttemptFailureCode(), record.getAttemptCreatedAt(), record.getAttemptCompletedAt());
        PaymentRefundSummaryResponse refund = record.getRefundId() == null ? null
                : new PaymentRefundSummaryResponse(record.getRefundId(), record.getRefundNo(),
                record.getRefundAmount(), record.getRefundCurrency(), record.getRefundChannel(),
                record.getRefundStatus(), record.getRefundReason(), record.getRefundAttemptCount(),
                record.getRefundLastFailureCode(), record.getRefundCompletedAt());
        return new AppointmentResponse(record.getId(), record.getAppointmentNo(), record.getScheduleId(),
                record.getAppointmentDate(), record.getStartTime(), record.getEndTime(), record.getStatus(),
                record.getVisitNumber(), record.getCreatedAt(), record.getPaymentOrderId(),
                record.getPaymentStatus(), workflow == null ? null : workflow.name(),
                record.getAmount(), record.getCurrency(), record.getPaymentAttemptCount(), attempt, refund,
                record.getPaymentExpiresAt(), record.getClosedAt(), serverTime, paymentExpired,
                pendingAndUnpaid && !paymentExpired,
                pendingAndCancellable || (confirmedAndPaid && serverTime.isBefore(cancellationDeadline)),
                patient, doctor);
    }

    private WorkflowStatus workflowStatus(BookingQueryRecord record) {
        String appointment = record.getStatus();
        String payment = record.getPaymentStatus();
        if ("PENDING_PAYMENT".equals(appointment) && "UNPAID".equals(payment)) {
            return WorkflowStatus.WAITING_FOR_PAYMENT;
        }
        if ("PENDING_PAYMENT".equals(appointment) && "PAYING".equals(payment)) {
            return WorkflowStatus.PAYMENT_PROCESSING;
        }
        if ("PENDING_PAYMENT".equals(appointment) && "PAID".equals(payment)) {
            return WorkflowStatus.CONFIRMING_APPOINTMENT;
        }
        if ("CONFIRMED".equals(appointment) && "PAID".equals(payment)) {
            return WorkflowStatus.CONFIRMED;
        }
        if (inactive(appointment) && "REFUND_PENDING".equals(payment)) {
            return WorkflowStatus.CANCELLED_REFUNDING;
        }
        if (inactive(appointment) && "REFUNDED".equals(payment)) {
            return WorkflowStatus.CANCELLED_REFUNDED;
        }
        if ("EXPIRED".equals(appointment) && "CLOSED".equals(payment)) {
            return WorkflowStatus.EXPIRED;
        }
        if (("CANCELLED".equals(appointment) || "CANCELLED_BY_HOSPITAL".equals(appointment))
                && "CLOSED".equals(payment)) {
            return null;
        }
        log.error("预约查询发现非法组合状态，appointmentOrderId={}, appointmentStatus={}, paymentStatus={}",
                record.getId(), appointment, payment);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    private boolean inactive(String appointment) {
        return "CANCELLED".equals(appointment) || "CANCELLED_BY_HOSPITAL".equals(appointment)
                || "EXPIRED".equals(appointment);
    }

    private void ensurePaymentIntegrity(BookingQueryRecord record) {
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
}

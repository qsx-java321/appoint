package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.bookingquery.model.WorkflowStatus;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.patient.service.ResidentIdCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
class AppointmentResponseAssembler {
    private final BookingWorkflowProjector projector;

    AppointmentResponse assemble(BookingQueryRecord record, LocalDateTime serverTime) {
        ensurePaymentIntegrity(record);
        AppointmentStatus appointment = AppointmentStatus.fromStored(record.getStatus());
        PaymentOrderStatus payment = PaymentOrderStatus.fromStored(record.getPaymentStatus());
        AppointmentDoctorResponse doctor = new AppointmentDoctorResponse(
                record.getDoctorId(), record.getDoctorNo(), record.getDoctorName(), record.getDoctorTitle(),
                new AppointmentDepartmentResponse(record.getDepartmentId(), record.getDepartmentName()),
                new AppointmentHospitalResponse(record.getHospitalId(), record.getHospitalName(),
                        record.getHospitalAddress()));
        AppointmentPatientResponse patient = new AppointmentPatientResponse(record.getPatientId(),
                record.getPatientName(), record.getPatientDocumentType(),
                ResidentIdCard.mask(record.getPatientDocumentNo()), record.getPatientContactPhone(),
                record.getPatientContactEmail());
        boolean paymentExpired = (appointment == AppointmentStatus.EXPIRED)
                || ((appointment == AppointmentStatus.PENDING_PAYMENT)
                && record.getPaymentExpiresAt() != null
                && !serverTime.isBefore(record.getPaymentExpiresAt()));
        boolean pendingAndUnpaid = (appointment == AppointmentStatus.PENDING_PAYMENT)
                && (payment == PaymentOrderStatus.UNPAID);
        boolean pendingAndCancellable = (appointment == AppointmentStatus.PENDING_PAYMENT)
                && ((payment == PaymentOrderStatus.UNPAID) || (payment == PaymentOrderStatus.PAYING));
        boolean confirmedAndPaid = (appointment == AppointmentStatus.CONFIRMED)
                && (payment == PaymentOrderStatus.PAID);
        WorkflowStatus workflow = projector.project(record);
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

}

package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.bookingquery.model.WorkflowStatus;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.catalog.model.BookingTimeRules;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.patient.service.ResidentIdCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import com.qsx.appoint.visit.domain.VisitRules;
import com.qsx.appoint.visit.domain.VisitStatus;

/**
 * 把只读组合行转换为预约详情：校验支付关联、脱敏、组装摘要与可操作标记。
 * <p>workflow交给Projector；本类的canPay/canCancel是兼容展示规则，不取代写入口重新校验。
 * @see BookingWorkflowProjector
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AppointmentResponseAssembler {
    private final BookingWorkflowProjector projector;

    /**
     * 校验支付归属后，组装预约凭证、流程状态、支付结果和操作标记。
     * @param record Mapper联查的完整预约行，归属已由查询用例校验
     * @param serverTime 本次请求统一采样的秒精度业务时间
     * @return 预约详情DTO
     * @throws com.qsx.appoint.common.BusinessException 支付缺失、归属不符或状态组合不合法
     */
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
        boolean paymentExpired = appointment == AppointmentStatus.EXPIRED
                || (appointment == AppointmentStatus.PENDING_PAYMENT
                && BookingTimeRules.isPaymentExpired(record.getPaymentExpiresAt(), serverTime));
        boolean pendingAndPayable = appointment == AppointmentStatus.PENDING_PAYMENT && payment.isUnpaid()
                && record.getPaymentExpiresAt() != null && !paymentExpired;
        WorkflowStatus workflow = projector.project(record);
        if (paymentExpired && payment.isUnpaid()) workflow = WorkflowStatus.EXPIRED;
        return new AppointmentResponse(record.getId(), record.getAppointmentNo(), record.getScheduleId(),
                record.getAppointmentDate(), record.getStartTime(), record.getEndTime(), record.getStatus(),
                record.getVisitNumber(), record.getCreatedAt(), record.getPaymentOrderId(),
                record.getPaymentStatus(), workflow == null ? null : workflow.name(),
                record.getAmount(), record.getCurrency(), record.getPaidAt(),
                record.getPaymentExpiresAt(), record.getClosedAt(), serverTime, paymentExpired,
                pendingAndPayable,
                pendingAndPayable,
                patient, doctor, record.getSessionType(), visit(record, appointment, serverTime));
    }
    private AppointmentVisitResponse visit(BookingQueryRecord record, AppointmentStatus status, LocalDateTime now) {
        if (status != AppointmentStatus.CONFIRMED) return null;
        VisitStatus progress = record.getVisitRecordId() == null
                ? VisitStatus.NOT_CHECKED_IN : VisitStatus.fromStored(record.getVisitStatus());
        return new AppointmentVisitResponse(progress.name(), record.getCheckedInAt(), record.getStartedAt(),
                record.getCompletedAt(), VisitRules.canCheckIn(status, record.getAppointmentDate(),
                record.getStartTime(), record.getEndTime(), progress, now),
                VisitRules.checkInOpensAt(record.getAppointmentDate(), record.getStartTime()),
                VisitRules.checkInClosesAt(record.getAppointmentDate(), record.getEndTime()));
    }
    /** 支付单必须存在且与预约账号相同；不能把LEFT JOIN缺失结果当作零金额未付单。 */
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

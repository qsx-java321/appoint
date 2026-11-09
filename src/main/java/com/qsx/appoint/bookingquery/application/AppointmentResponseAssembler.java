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
     * 校验支付归属后，组装预约凭证、流程状态、尝试/退款摘要和操作标记。
     * @param record Mapper联查的完整预约行，归属已由查询用例校验
     * @param serverTime 本次请求统一采样的秒精度业务时间
     * @return 详情DTO；未发起尝试或退款时对应摘要为null
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
        boolean paymentExpired = (appointment == AppointmentStatus.EXPIRED)
                || ((appointment == AppointmentStatus.PENDING_PAYMENT)
                && record.getPaymentExpiresAt() != null
                && !serverTime.isBefore(record.getPaymentExpiresAt()));
        boolean pendingAndUnpaid = (appointment == AppointmentStatus.PENDING_PAYMENT)
                && (payment == PaymentOrderStatus.UNPAID);
        // 保留既有展示契约：到期但尚未扫描的UNPAID/PAYING仍显示可取消；写入口会按时刻过期关闭。
        boolean pendingAndCancellable = (appointment == AppointmentStatus.PENDING_PAYMENT)
                && ((payment == PaymentOrderStatus.UNPAID) || (payment == PaymentOrderStatus.PAYING));
        boolean confirmedAndPaid = (appointment == AppointmentStatus.CONFIRMED)
                && (payment == PaymentOrderStatus.PAID);
        // 流程状态由预约+支付组合决定，退款摘要只展示执行细节，不参与重定义workflow。
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

package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.bookingquery.model.WorkflowStatus;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将预约状态与支付状态组合为用户可见流程状态的纯投影。
 * <p>不写数据库、不执行补偿，也不根据退款摘要重定义资金状态；非法组合报完整性错误。
 */
@Slf4j
@Component
class BookingWorkflowProjector {
    /**
     * 计算组合展示状态，不把“收款成功”和“预约已确认”混为同一阶段。
     * @param record 包含预约与支付存储状态的只读行
     * @return 对应workflow；历史CANCELLED/CANCELLED_BY_HOSPITAL+CLOSED返回null
     * @throws com.qsx.appoint.common.BusinessException 未知存储值或未定义组合，错误码INTERNAL_ERROR
     */
    WorkflowStatus project(BookingQueryRecord record) {
        AppointmentStatus appointment = AppointmentStatus.fromStored(record.getStatus());
        PaymentOrderStatus payment = PaymentOrderStatus.fromStored(record.getPaymentStatus());
        if (appointment == AppointmentStatus.PENDING_PAYMENT && payment.isUnpaid()) {
            return WorkflowStatus.WAITING_FOR_PAYMENT;
        }
        if (appointment == AppointmentStatus.CONFIRMED && payment == PaymentOrderStatus.PAID) {
            return WorkflowStatus.CONFIRMED;
        }
        if (appointment == AppointmentStatus.EXPIRED && payment == PaymentOrderStatus.CLOSED) {
            return WorkflowStatus.EXPIRED;
        }
        // 旧版过渡和终态仍可读取，保留原始状态供查阅。
        if ((appointment == AppointmentStatus.PENDING_PAYMENT && payment == PaymentOrderStatus.PAID)
                || ((appointment == AppointmentStatus.CANCELLED || appointment == AppointmentStatus.CANCELLED_BY_HOSPITAL
                        || appointment == AppointmentStatus.EXPIRED)
                && (payment == PaymentOrderStatus.CLOSED || payment == PaymentOrderStatus.REFUND_PENDING
                        || payment == PaymentOrderStatus.REFUNDED))) {
            return null;
        }
        log.error("预约查询发现非法组合状态，appointmentOrderId={}, appointmentStatus={}, paymentStatus={}",
                record.getId(), appointment, payment);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

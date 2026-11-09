package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.bookingquery.model.WorkflowStatus;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class BookingWorkflowProjector {
    WorkflowStatus project(BookingQueryRecord record) {
        AppointmentStatus appointment = AppointmentStatus.fromStored(record.getStatus());
        PaymentOrderStatus payment = PaymentOrderStatus.fromStored(record.getPaymentStatus());
        if ((appointment == AppointmentStatus.PENDING_PAYMENT) && (payment == PaymentOrderStatus.UNPAID)) {
            return WorkflowStatus.WAITING_FOR_PAYMENT;
        }
        if ((appointment == AppointmentStatus.PENDING_PAYMENT) && (payment == PaymentOrderStatus.PAYING)) {
            return WorkflowStatus.PAYMENT_PROCESSING;
        }
        if ((appointment == AppointmentStatus.PENDING_PAYMENT) && (payment == PaymentOrderStatus.PAID)) {
            return WorkflowStatus.CONFIRMING_APPOINTMENT;
        }
        if ((appointment == AppointmentStatus.CONFIRMED) && (payment == PaymentOrderStatus.PAID)) {
            return WorkflowStatus.CONFIRMED;
        }
        if (inactive(appointment) && (payment == PaymentOrderStatus.REFUND_PENDING)) {
            return WorkflowStatus.CANCELLED_REFUNDING;
        }
        if (inactive(appointment) && (payment == PaymentOrderStatus.REFUNDED)) {
            return WorkflowStatus.CANCELLED_REFUNDED;
        }
        if ((appointment == AppointmentStatus.EXPIRED) && (payment == PaymentOrderStatus.CLOSED)) {
            return WorkflowStatus.EXPIRED;
        }
        if (((appointment == AppointmentStatus.CANCELLED) || (appointment == AppointmentStatus.CANCELLED_BY_HOSPITAL))
                && (payment == PaymentOrderStatus.CLOSED)) {
            return null;
        }
        log.error("预约查询发现非法组合状态，appointmentOrderId={}, appointmentStatus={}, paymentStatus={}",
                record.getId(), appointment, payment);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    private boolean inactive(AppointmentStatus appointment) {
        return (appointment == AppointmentStatus.CANCELLED) || (appointment == AppointmentStatus.CANCELLED_BY_HOSPITAL)
                || (appointment == AppointmentStatus.EXPIRED);
    }

}

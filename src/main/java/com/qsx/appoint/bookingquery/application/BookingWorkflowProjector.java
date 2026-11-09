package com.qsx.appoint.bookingquery.application;

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

}

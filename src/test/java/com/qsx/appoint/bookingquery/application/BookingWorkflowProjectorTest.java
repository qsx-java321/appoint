package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.bookingquery.model.WorkflowStatus;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingWorkflowProjectorTest {
    private final BookingWorkflowProjector projector = new BookingWorkflowProjector();

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, UNPAID, WAITING_FOR_PAYMENT",
            "PENDING_PAYMENT, PAYING, PAYMENT_PROCESSING",
            "PENDING_PAYMENT, PAID, CONFIRMING_APPOINTMENT",
            "CONFIRMED, PAID, CONFIRMED",
            "CANCELLED, REFUND_PENDING, CANCELLED_REFUNDING",
            "EXPIRED, REFUND_PENDING, CANCELLED_REFUNDING",
            "CANCELLED_BY_HOSPITAL, REFUND_PENDING, CANCELLED_REFUNDING",
            "CANCELLED, REFUNDED, CANCELLED_REFUNDED",
            "EXPIRED, REFUNDED, CANCELLED_REFUNDED",
            "CANCELLED_BY_HOSPITAL, REFUNDED, CANCELLED_REFUNDED",
            "EXPIRED, CLOSED, EXPIRED"
    })
    void projectsSupportedStates(String appointment, String payment, WorkflowStatus expected) {
        assertThat(projector.project(record(appointment, payment))).isEqualTo(expected);
    }

    @Test
    void cancelledClosedReturnsNullForCompatibility() {
        assertThat(projector.project(record("CANCELLED", "CLOSED"))).isNull();
        assertThat(projector.project(record("CANCELLED_BY_HOSPITAL", "CLOSED"))).isNull();
    }

    @Test
    void inconsistentCombinationIsRejected() {
        assertThatThrownBy(() -> projector.project(record("CONFIRMED", "UNPAID")))
                .isInstanceOf(BusinessException.class);
    }

    private BookingQueryRecord record(String appointment, String payment) {
        BookingQueryRecord record = new BookingQueryRecord();
        record.setId(1L);
        record.setStatus(appointment);
        record.setPaymentStatus(payment);
        return record;
    }
}

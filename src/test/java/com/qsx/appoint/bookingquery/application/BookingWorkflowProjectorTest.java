package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.bookingquery.model.WorkflowStatus;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 直接验证持久化状态组合到查询状态的投影，包含非法组合；不把每种组合都放进Spring上下文。 */
class BookingWorkflowProjectorTest {
    private final BookingWorkflowProjector projector = new BookingWorkflowProjector();

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, UNPAID, WAITING_FOR_PAYMENT",
            "CONFIRMED, PAID, CONFIRMED",
            "EXPIRED, CLOSED, EXPIRED"
    })
    void projectsSupportedStates(String appointment, String payment, WorkflowStatus expected) {
        assertThat(projector.project(record(appointment, payment))).isEqualTo(expected);
    }

    @Test
    void cancelledClosedReturnsNull() {
        assertThat(projector.project(record("CANCELLED", "CLOSED"))).isNull();
    }

    @Test
    void inconsistentCombinationIsRejected() {
        assertThatThrownBy(() -> projector.project(record("CONFIRMED", "UNPAID")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void everyOtherPersistedStateCombinationIsRejected() {
        var allowed = java.util.Set.of(
                "PENDING_PAYMENT/UNPAID", "CONFIRMED/PAID",
                "EXPIRED/CLOSED", "CANCELLED/CLOSED");
        for (String appointment : java.util.List.of("PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED")) {
            for (var payment : com.qsx.appoint.payment.domain.PaymentOrderStatus.values()) {
                if (!allowed.contains(appointment + "/" + payment.name())) {
                    assertThatThrownBy(() -> projector.project(record(appointment, payment.name())))
                            .as("%s/%s", appointment, payment)
                            .isInstanceOfSatisfying(BusinessException.class,
                                    exception -> assertThat(exception.getErrorCode())
                                            .isEqualTo(com.qsx.appoint.common.ErrorCode.INTERNAL_ERROR));
                }
            }
        }
    }

    private BookingQueryRecord record(String appointment, String payment) {
        BookingQueryRecord record = new BookingQueryRecord();
        record.setId(1L);
        record.setStatus(appointment);
        record.setPaymentStatus(payment);
        return record;
    }
}

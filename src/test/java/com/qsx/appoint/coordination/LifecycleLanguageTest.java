package com.qsx.appoint.coordination;

import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.PaymentAttemptStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LifecycleLanguageTest {
    @Test
    void stableStorageAndRefundReasonLiteralsRemainCompatible() {
        assertThat(AppointmentStatus.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED", "CANCELLED_BY_HOSPITAL");
        assertThat(RefundReason.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("APPOINTMENT_CANCELLED", "LATE_PAYMENT", "APPOINTMENT_NOT_CONFIRMABLE", "RECONCILIATION_CLOSED_PAID");
        for (var status : AppointmentStatus.values()) assertThat(AppointmentStatus.fromStored(status.name())).isSameAs(status);
        for (var status : PaymentOrderStatus.values()) assertThat(PaymentOrderStatus.fromStored(status.name())).isSameAs(status);
        for (var status : PaymentAttemptStatus.values()) assertThat(PaymentAttemptStatus.fromStored(status.name())).isSameAs(status);
        assertThat(AppointmentCloseReason.USER_CANCELLED.status().name()).isEqualTo("CANCELLED");
        assertThat(AppointmentCloseReason.PAYMENT_EXPIRED.status().name()).isEqualTo("EXPIRED");
    }

    @Test
    void unknownAndNullStoredStatesRemainInternalErrors() {
        for (String value : new String[]{"UNKNOWN", null}) {
            assertInternal(() -> AppointmentStatus.fromStored(value));
            assertInternal(() -> PaymentOrderStatus.fromStored(value));
            assertInternal(() -> PaymentAttemptStatus.fromStored(value));
        }
    }

    private void assertInternal(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }
}

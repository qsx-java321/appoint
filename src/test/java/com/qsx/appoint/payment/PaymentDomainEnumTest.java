package com.qsx.appoint.payment;

import com.qsx.appoint.bookingquery.model.WorkflowStatus;
import com.qsx.appoint.payment.domain.PaymentAttemptStatus;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.RefundStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentDomainEnumTest {
    @Test
    void targetPaymentStateVocabularyMatchesTheAcceptedDesign() {
        assertThat(names(PaymentOrderStatus.values())).containsExactlyInAnyOrder(
                "UNPAID", "PAYING", "PAID", "CLOSED", "REFUND_PENDING", "REFUNDED");
        assertThat(names(PaymentAttemptStatus.values())).containsExactlyInAnyOrder(
                "CREATED", "PROCESSING", "SUCCEEDED", "FAILED", "CLOSED");
        assertThat(names(RefundStatus.values())).containsExactlyInAnyOrder(
                "PENDING", "SUCCEEDED", "FAILED");
        assertThat(names(PaymentChannel.values())).containsExactlyInAnyOrder(
                "MOCK", "WECHAT_PAY", "ALIPAY");
        assertThat(names(WorkflowStatus.values())).containsExactlyInAnyOrder(
                "WAITING_FOR_PAYMENT", "PAYMENT_PROCESSING", "CONFIRMING_APPOINTMENT",
                "CONFIRMED", "CANCELLED_REFUNDING", "CANCELLED_REFUNDED", "EXPIRED");
    }

    private Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}

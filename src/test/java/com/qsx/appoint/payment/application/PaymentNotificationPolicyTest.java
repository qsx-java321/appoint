package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.port.GatewayPaymentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentNotificationPolicyTest {
    private final PaymentNotificationPolicy policy = new PaymentNotificationPolicy();

    @ParameterizedTest
    @CsvSource({
            "SUCCEEDED, PROCESSING, PAYING, SUCCEED",
            "SUCCEEDED, PROCESSING, CLOSED, LATE_PAYMENT",
            "SUCCEEDED, SUCCEEDED, PAID, ALREADY_SUCCEEDED",
            "SUCCEEDED, SUCCEEDED, REFUND_PENDING, REJECT_STATE",
            "SUCCEEDED, FAILED, PAYING, REJECT_STATE",
            "FAILED, PROCESSING, PAYING, FAIL",
            "FAILED, SUCCEEDED, REFUND_PENDING, IGNORE_LATE_FAILURE",
            "FAILED, PROCESSING, PAID, IGNORE_LATE_FAILURE",
            "FAILED, PROCESSING, CLOSED, REJECT_STATE"
    })
    void preservesNotificationDecisions(GatewayPaymentStatus result, String attempt, String payment,
                                        PaymentNotificationPolicy.Decision expected) {
        assertThat(policy.decide(result, attempt, payment)).isEqualTo(expected);
    }
}

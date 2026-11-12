package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.port.GatewayPaymentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

/** 直接验证通知决策优先级、金额/币种和成功状态保护；真实写入由事务测试验证。 */
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
    void preservesNotificationDecisions(GatewayPaymentStatus result, com.qsx.appoint.payment.domain.PaymentAttemptStatus attempt, com.qsx.appoint.payment.domain.PaymentOrderStatus payment,
                                        PaymentNotificationPolicy.Decision expected) {
        assertThat(policy.decide(result, attempt, payment)).isEqualTo(expected);
    }
    @org.junit.jupiter.api.Test
    void unknownAndAmountDecisionsPrecedeStateRulesAndIgnoreDecimalScale() {
        var notice = new com.qsx.appoint.payment.port.VerifiedPaymentNotification("notice", "order", "txn",
                GatewayPaymentStatus.SUCCEEDED, new java.math.BigDecimal("10.0"), "CNY", java.time.LocalDateTime.now());
        assertThat(policy.decide(notice, null)).isEqualTo(PaymentNotificationPolicy.Decision.REJECT_UNKNOWN_ORDER);
        var mismatch = new PaymentNotificationPolicy.Facts(new java.math.BigDecimal("11.00"), "CNY",
                com.qsx.appoint.payment.domain.PaymentAttemptStatus.FAILED,
                com.qsx.appoint.payment.domain.PaymentOrderStatus.REFUNDED);
        assertThat(policy.decide(notice, mismatch)).isEqualTo(PaymentNotificationPolicy.Decision.REJECT_AMOUNT_OR_CURRENCY);
        var matching = new PaymentNotificationPolicy.Facts(new java.math.BigDecimal("10.00"), "CNY",
                com.qsx.appoint.payment.domain.PaymentAttemptStatus.PROCESSING,
                com.qsx.appoint.payment.domain.PaymentOrderStatus.PAYING);
        assertThat(policy.decide(notice, matching)).isEqualTo(PaymentNotificationPolicy.Decision.SUCCEED);
        var foreignCurrency = new PaymentNotificationPolicy.Facts(new java.math.BigDecimal("10.00"), "USD",
                matching.attempt(), matching.payment());
        assertThat(policy.decide(notice, foreignCurrency)).isEqualTo(PaymentNotificationPolicy.Decision.REJECT_AMOUNT_OR_CURRENCY);
    }

}

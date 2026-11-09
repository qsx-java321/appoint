package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.PaymentAttemptStatus;
import com.qsx.appoint.payment.port.GatewayPaymentStatus;

/** 回调状态组合的纯决策；数据完整性与幂等由调用方负责。 */
final class PaymentNotificationPolicy {
    enum Decision { SUCCEED, LATE_PAYMENT, ALREADY_SUCCEEDED, FAIL, IGNORE_LATE_FAILURE, REJECT_STATE, REJECT_AMOUNT_OR_CURRENCY, REJECT_UNKNOWN_ORDER }

    record Facts(java.math.BigDecimal amount, String currency,
                 PaymentAttemptStatus attempt, PaymentOrderStatus payment) { }

    Decision decide(com.qsx.appoint.payment.port.VerifiedPaymentNotification notification, Facts facts) {
        if (facts == null) return Decision.REJECT_UNKNOWN_ORDER;
        if (facts.amount().compareTo(notification.amount()) != 0 || !facts.currency().equals(notification.currency())) {
            return Decision.REJECT_AMOUNT_OR_CURRENCY;
        }
        return decide(notification.status(), facts.attempt(), facts.payment());
    }

    Decision decide(GatewayPaymentStatus result, PaymentAttemptStatus attempt, PaymentOrderStatus payment) {
        if (result == GatewayPaymentStatus.SUCCEEDED) {
            if (payment == PaymentOrderStatus.PAID && attempt == PaymentAttemptStatus.SUCCEEDED) return Decision.ALREADY_SUCCEEDED;
            if (attempt == PaymentAttemptStatus.PROCESSING && payment == PaymentOrderStatus.CLOSED) return Decision.LATE_PAYMENT;
            if (attempt == PaymentAttemptStatus.PROCESSING && payment == PaymentOrderStatus.PAYING) return Decision.SUCCEED;
        }
        if (result == GatewayPaymentStatus.FAILED) {
            if (attempt == PaymentAttemptStatus.SUCCEEDED || payment == PaymentOrderStatus.PAID) return Decision.IGNORE_LATE_FAILURE;
            if (attempt == PaymentAttemptStatus.PROCESSING && payment == PaymentOrderStatus.PAYING) return Decision.FAIL;
        }
        return Decision.REJECT_STATE;
    }
}

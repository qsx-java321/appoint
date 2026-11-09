package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.port.GatewayPaymentStatus;

/** 回调状态组合的纯决策；数据完整性与幂等由调用方负责。 */
final class PaymentNotificationPolicy {
    enum Decision { SUCCEED, LATE_PAYMENT, ALREADY_SUCCEEDED, FAIL, IGNORE_LATE_FAILURE, REJECT_STATE }

    Decision decide(GatewayPaymentStatus result, String attempt, String payment) {
        if (result == GatewayPaymentStatus.SUCCEEDED) {
            if ("PAID".equals(payment) && "SUCCEEDED".equals(attempt)) return Decision.ALREADY_SUCCEEDED;
            if ("PROCESSING".equals(attempt) && "CLOSED".equals(payment)) return Decision.LATE_PAYMENT;
            if ("PROCESSING".equals(attempt) && "PAYING".equals(payment)) return Decision.SUCCEED;
        }
        if (result == GatewayPaymentStatus.FAILED) {
            if ("SUCCEEDED".equals(attempt) || "PAID".equals(payment)) return Decision.IGNORE_LATE_FAILURE;
            if ("PROCESSING".equals(attempt) && "PAYING".equals(payment)) return Decision.FAIL;
        }
        return Decision.REJECT_STATE;
    }
}

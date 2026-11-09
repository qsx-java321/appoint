package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.PaymentAttemptStatus;
import com.qsx.appoint.payment.port.GatewayPaymentStatus;

/**
 * 支付通知的纯状态决策，输入已验签通知和锁后业务事实。
 * <p>先判断未知订单及金额/币种，再判断状态；幂等摘要、关联完整性和事务写入由调用者处理。
 */
final class PaymentNotificationPolicy {
    enum Decision { SUCCEED, LATE_PAYMENT, ALREADY_SUCCEEDED, FAIL, IGNORE_LATE_FAILURE, REJECT_STATE, REJECT_AMOUNT_OR_CURRENCY, REJECT_UNKNOWN_ORDER }

    record Facts(java.math.BigDecimal amount, String currency,
                 PaymentAttemptStatus attempt, PaymentOrderStatus payment) { }

    /**
     * 先校验未知订单及金额/币种，再判断可信通知与支付/尝试状态组合。
     * @param notification 已验签字段
     * @param facts 锁后资金事实；null表示未知渠道订单
     * @return 纯动作，不记录审计或修改数据库
     */
    Decision decide(com.qsx.appoint.payment.port.VerifiedPaymentNotification notification, Facts facts) {
        if (facts == null) return Decision.REJECT_UNKNOWN_ORDER;
        if (facts.amount().compareTo(notification.amount()) != 0 || !facts.currency().equals(notification.currency())) {
            return Decision.REJECT_AMOUNT_OR_CURRENCY;
        }
        return decide(notification.status(), facts.attempt(), facts.payment());
    }

    /**
     * 对成功/失败及已锁支付、尝试状态作纯判断。
     * <p>PROCESSING+CLOSED成功转迟到退款；迟到失败不能覆盖已成功资金事实，其他非法组合拒绝。
     */
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

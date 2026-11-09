package com.qsx.appoint.payment.domain;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

/** 持久化状态在模块入口显式转换，未知值属于内部完整性错误。 */
public enum PaymentOrderStatus {
    UNPAID, PAYING, PAID, CLOSED, REFUND_PENDING, REFUNDED;

    public static PaymentOrderStatus fromStored(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}

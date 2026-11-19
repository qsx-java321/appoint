package com.qsx.appoint.payment.domain;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

/** 支付单资金整体状态，区别于一次尝试状态和预约展示workflow。 */
public enum PaymentOrderStatus {
    /** 未收款且可按其他资格发起尝试。 */
    UNPAID,
    /** 旧版已发起尝试，尚未确认收款。 */
    PAYING,
    /** 已确认收款。 */
    PAID,
    /** 未付关闭。 */
    CLOSED,
    /** 历史状态，退款曾经申请。 */
    REFUND_PENDING,
    /** 历史状态，旧版已记录退款。 */
    REFUNDED;

    public boolean isUnpaid() {
        return this == UNPAID || this == PAYING;
    }

    /**
     * 从既有存储字符串显式转换状态，不做默认值或大小写兜底。
     * @param value 数据库或稳定事实中的状态原值
     * @return 匹配的领域状态
     * @throws com.qsx.appoint.common.BusinessException null或未知字符串，错误码INTERNAL_ERROR
     */
    public static PaymentOrderStatus fromStored(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}

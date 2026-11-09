package com.qsx.appoint.payment.domain;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

/** 支付单资金整体状态，区别于一次尝试状态和预约展示workflow。 */
public enum PaymentOrderStatus {
    /** 未收款且可按其他资格发起尝试。 */
    UNPAID,
    /** 本地存在发起中的尝试，尚未确认收款。 */
    PAYING,
    /** 已确认收款，预约可能仍在确认中。 */
    PAID,
    /** 本地未付关闭，仍可能接到PROCESSING尝试的迟到收款。 */
    CLOSED,
    /** 退款已申请，渠道尚未完成。 */
    REFUND_PENDING,
    /** 本地已记录渠道退款成功。 */
    REFUNDED;

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

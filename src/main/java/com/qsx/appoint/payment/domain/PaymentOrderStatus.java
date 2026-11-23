package com.qsx.appoint.payment.domain;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

/** 支付单资金整体状态，区别于预约展示workflow。 */
public enum PaymentOrderStatus {
    /** 未收款且可按资格发起支付。 */
    UNPAID,
    /** 已确认收款。 */
    PAID,
    /** 未付关闭。 */
    CLOSED;

    public boolean isUnpaid() {
        return this == UNPAID;
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

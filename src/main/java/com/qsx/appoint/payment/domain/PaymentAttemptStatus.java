package com.qsx.appoint.payment.domain;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

/** 单次渠道尝试状态，Entity/Mapper继续使用同名稳定存储字符串。 */
public enum PaymentAttemptStatus {
    /** T1已创建，渠道会话待T2回填。 */
    CREATED,
    /** 已保存渠道会话，等待签名通知。 */
    PROCESSING,
    /** 已由渠道成功通知确认。 */
    SUCCEEDED,
    /** 下单或渠道支付失败，可保留历史审计。 */
    FAILED,
    /** 兼容的尝试关闭状态，不等同支付单CLOSED。 */
    CLOSED;

    /**
     * 从既有存储字符串显式转换状态，不做默认值或大小写兜底。
     * @param value 数据库或稳定事实中的状态原值
     * @return 匹配的领域状态
     * @throws com.qsx.appoint.common.BusinessException null或未知字符串，错误码INTERNAL_ERROR
     */
    public static PaymentAttemptStatus fromStored(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}

package com.qsx.appoint.appointment.domain;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

/**
 * 预约单稳定状态枚举，数据库和HTTP保存原字符串。
 * <p>当前流程只产生待支付、已确认、用户取消和超时过期四种状态。
 */
public enum AppointmentStatus {
    /** 已暂占名额，等待付款或支付成功事件确认。 */
    PENDING_PAYMENT,
    /** 已确认并分配就诊序号，占用confirmed名额。 */
    CONFIRMED,
    /** 用户取消后终态。 */
    CANCELLED,
    /** 截止后的关闭终态，包含迟到支付释放。 */
    EXPIRED;

    /**
     * 从既有存储字符串显式转换状态，不做默认值或大小写兜底。
     * @param value 数据库或稳定事实中的状态原值
     * @return 匹配的领域状态
     * @throws com.qsx.appoint.common.BusinessException null或未知字符串，错误码INTERNAL_ERROR
     */
    public static AppointmentStatus fromStored(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}

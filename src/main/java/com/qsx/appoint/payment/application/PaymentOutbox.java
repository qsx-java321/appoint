package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.contract.event.*;
import java.time.LocalDateTime;

/** 事务内事件记录契约。必须已有事务；序列化/持久化失败向外传播，随业务一起回滚。
 * 业务入口负责防重；本接口每次调用生成一个新事件，不能用于发布重试或消息重放。 */
public interface PaymentOutbox {
    /** 记录 PaymentSucceeded；加入调用方事务，返回新事件ID，不表示已提交或已发送。 */
    String recordPaymentSucceeded(PaymentSucceededPayload payload, LocalDateTime occurredAt);
    /** 记录 RefundRequested；加入调用方事务，返回新事件ID，不表示已提交或已发送。 */
    String recordRefundRequested(RefundRequestedPayload payload, LocalDateTime occurredAt);
    /** 记录 RefundSucceeded；加入调用方事务，返回新事件ID，不表示已提交或已发送。 */
    String recordRefundSucceeded(RefundSucceededPayload payload, LocalDateTime occurredAt);
    /** 记录 RefundFailed；加入调用方事务，返回新事件ID，不表示已提交或已发送。 */
    String recordRefundFailed(RefundFailedPayload payload, LocalDateTime occurredAt);
}

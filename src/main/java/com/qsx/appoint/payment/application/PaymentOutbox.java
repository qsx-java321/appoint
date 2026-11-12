package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.contract.event.*;
import java.time.LocalDateTime;

/** 事务内事件记录契约。必须已有事务；序列化/持久化失败向外传播，随业务一起回滚。
 * 业务入口负责防重；本接口每次调用生成一个新事件，不能用于发布重试或消息重放。 */
public interface PaymentOutbox {
    /**
     * 记录 PaymentSucceeded，序列化或数据库异常令调用方事务回滚。
     * @param payload 来自已锁业务事实的载荷，不重新查库/计算金额
     * @param occurredAt 本系统确定的事实时间，不能改用消息发送时间
     * @return 本次新事件ID；不表示事务已提交、Broker已接收或业务已消费
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     */
    String recordPaymentSucceeded(PaymentSucceededPayload payload, LocalDateTime occurredAt);
    /**
     * 记录 RefundRequested，序列化或数据库异常令调用方事务回滚。
     * @param payload 来自已锁业务事实的载荷，不重新查库/计算金额
     * @param occurredAt 本系统确定的事实时间，不能改用消息发送时间
     * @return 本次新事件ID；不表示事务已提交、Broker已接收或业务已消费
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     */
    String recordRefundRequested(RefundRequestedPayload payload, LocalDateTime occurredAt);
    /**
     * 记录 RefundSucceeded，序列化或数据库异常令调用方事务回滚。
     * @param payload 来自已锁业务事实的载荷，不重新查库/计算金额
     * @param occurredAt 本系统确定的事实时间，不能改用消息发送时间
     * @return 本次新事件ID；不表示事务已提交、Broker已接收或业务已消费
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     */
    String recordRefundSucceeded(RefundSucceededPayload payload, LocalDateTime occurredAt);
    /**
     * 记录 RefundFailed，序列化或数据库异常令调用方事务回滚。
     * @param payload 来自已锁业务事实的载荷，不重新查库/计算金额
     * @param occurredAt 本系统确定的事实时间，不能改用消息发送时间
     * @return 本次新事件ID；不表示事务已提交、Broker已接收或业务已消费
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     */
    String recordRefundFailed(RefundFailedPayload payload, LocalDateTime occurredAt);
}

package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import java.time.LocalDateTime;

/**
 * 预约完整生命周期操作，供协调层在同一数据库事务内调用。
 *
 * <p>库存、有效占用和就诊序号都由本模块维护；调用者只选择确认或关闭动作。
 * 锁定及写方法要求已有事务，find 仅返回普通事实快照。支付资格由协调层判断。
 * @see AppointmentLifecycleCase
 */
public interface AppointmentLifecycle {
    /**
     * 按预约单ID读取业务事实，不加行锁。
     * @param appointmentOrderId 预约单主键，区别于支付单ID
     * @return 当前事实快照；预约不存在返回 null，结果不能用于生命周期写入
     */
    AppointmentLifecycleCase find(Long appointmentOrderId);

    /**
     * 按场次→预约顺序加锁，并登记当前事务的写入上下文。
     * <p>返回的原对象仅在本事务内有效；find结果、复制对象和其他事务的Case不能替代。
     * @param appointmentOrderId 要处理的预约单主键
     * @return 本事务锁定的预约；预约不存在返回 null
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     * @throws com.qsx.appoint.common.BusinessException 场次缺失或关联不一致
     */
    AppointmentLifecycleCase lock(Long appointmentOrderId);

    /**
     * 确认满足支付资格的待支付预约，完成暂占转确认并分配就诊序号。
     * <p>调用者应先判断支付事实和截止资格。本操作不访问支付模块；序号在场次锁内分配，
     * 不因后续取消而复用。所有写入参加外层事务，失败一并回滚；成功后该Case不再授权写入。
     * @param appointment 当前事务lock返回的原对象，状态须为PENDING_PAYMENT
     * @return 预约ID及分配的就诊序号，不为 null
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     * @throws com.qsx.appoint.common.BusinessException Case无效、状态不符或条件更新未命中
     */
    AppointmentConfirmation confirm(AppointmentLifecycleCase appointment);

    /**
     * 关闭待支付预约，同时释放暂占并删除有效占用。
     * <p>协调层在同一事务内关闭支付单。失败回滚全部预约变更。
     * @param appointment 当前事务lock返回的待支付预约原对象
     * @param reason 关闭原因，决定预约终态为CANCELLED或EXPIRED
     * @param closedAt 协调层采样的业务关闭时间，沿用同一次决策时间
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     * @throws com.qsx.appoint.common.BusinessException Case/状态无效或库存、预约、占用更新不满足预期
     */
    void closePending(AppointmentLifecycleCase appointment, AppointmentCloseReason reason, LocalDateTime closedAt);
}

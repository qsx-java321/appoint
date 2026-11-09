package com.qsx.appoint.coordination.application;

/**
 * 跨预约与支付的生命周期用例，负责归属校验、组合状态决策及同库事务编排。
 * <p>调用完整预约/支付interface，不直接分配序号、修改库存或操作写模块Entity。
 */
public interface BookingLifecycleCoordinator {
    /**
     * 取消本人预约，按状态与时刻关闭未确认预约或取消已确认预约，并在需要时申请退款。
     * @param userId 当前账号用户ID，先于支付完整性检查进行归属校验
     * @param appointmentOrderId 目标预约单ID
     * @return true表示已申请或仍处于异步退款；false表示无需退款或退款已完成，不表示取消失败
     * @throws com.qsx.appoint.common.BusinessException 不存在、非本人、取消截止已到或组合事实不一致
     */
    boolean cancelOwned(Long userId, Long appointmentOrderId);

    /**
     * 处理一张扫描候选，锁后重新判断是否到期以及支付真相。
     * @param appointmentOrderId 候选预约单ID，允许候选在处理前已经变化或不存在
     * @return 实际完成关闭、恢复确认或关闭退款时true；不存在、非待支付、未到期时false
     * @throws com.qsx.appoint.common.BusinessException 需处理时发现支付关联或状态不一致
     */
    boolean expireIfDue(Long appointmentOrderId);

    /**
     * 恢复已支付但仍待确认的预约，不要求当前已经到期。
     * @param appointmentOrderId 对账找出的候选预约ID
     * @return 完成确认或迟到关闭退款时true；已不满足待支付已收款条件时false
     * @throws com.qsx.appoint.common.BusinessException 支付关联/组合事实不一致
     */
    boolean reconcilePaidPending(Long appointmentOrderId);

    /**
     * 为已关闭但仍为PAID的预约确保全额退款，保持预约终态与已释放库存。
     * @param appointmentOrderId 已关闭已付的候选预约ID
     * @return 通过退款接口处理候选时true；不存在、未关闭或支付已非PAID时false
     * @throws com.qsx.appoint.common.BusinessException 支付关联不一致或退款前提不满足
     */
    boolean ensureRefundForClosedPaid(Long appointmentOrderId);
}

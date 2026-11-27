package com.qsx.appoint.inventory.application;

/**
 * 场次库存端口：负责场次懒生成、占号、锁定、确认和释放。
 *
 * <p>写方法均要求调用方已有事务并加入该事务；调用方必须保持“场次 → 预约 → 支付”的锁序。
 * 本端口不读取系统时钟，占号时间由 {@link ReserveCommand#checkedAt()} 传入。
 */
public interface ScheduleInventory {
    /**
     * 查找或首次生成场次，并暂占一个名额。
     *
     * <p>场次不存在时按统一预约设置生成；随后用条件更新取得场次行锁并完成占号。
     * 占号后重新读取场次作为快照，费用缺失、非正或币种不是 CNY 视为号源不可用。
     * @param command 医生、日期、场次类型和调用方采样的业务时间
     * @return 已暂占的场次库存快照，不为 null
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     * @throws com.qsx.appoint.common.BusinessException 号源不可占、场次生成失败或费用不合法
     */
    InventorySnapshot reserve(ReserveCommand command);

    /**
     * 锁定场次行并返回库存快照，供后续预约行锁和确认/释放使用。
     *
     * <p>必须在锁定预约单之前调用，以保持场次→预约→支付的锁序。
     * @param scheduleId 场次主键
     * @return 已锁场次快照；场次不存在返回 null，由调用方决定错误语义
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     */
    InventorySnapshot lock(Long scheduleId);

    /**
     * 将暂占名额转为确认名额，并返回本次分配的就诊序号。
     *
     * <p>调用方必须先通过 {@link #lock(Long)} 取得锁定的场次快照，并在锁定预约单后调用本方法。
     * @param scheduleId 场次主键
     * @param expectedNextVisitNumber 锁定场次时读取的期望序号
     * @return 本次分配的就诊序号
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     * @throws com.qsx.appoint.common.BusinessException 条件更新未命中或库存状态不满足预期
     */
    int confirm(Long scheduleId, int expectedNextVisitNumber);

    /**
     * 释放一个暂占名额。
     *
     * @param scheduleId 场次主键
     * @throws org.springframework.transaction.IllegalTransactionStateException 调用方没有事务
     * @throws com.qsx.appoint.common.BusinessException 条件更新未命中或库存状态不满足预期
     */
    void release(Long scheduleId);
}

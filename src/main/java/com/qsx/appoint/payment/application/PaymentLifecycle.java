package com.qsx.appoint.payment.application;

/**
 * 支付生命周期的稳定事实与关闭interface，供协调事务使用。
 * <p>锁定/关闭要求MANDATORY；不暴露Entity，不在这里执行渠道支付或退款。
 */
public interface PaymentLifecycle {
    /**
     * 普通查询支付业务事实，不加行锁或登记写授权。
     * @param paymentOrderId 支付单主键
     * @return 支付快照；不存在为null，不能传给closeUnpaid授权写入
     */
    PaymentLifecycleCase find(Long paymentOrderId);

    /**
     * 在已有协调事务中按预约关联锁定支付父记录，获取本事务Case。
     * @param appointmentOrderId 预约单主键，调用前应先锁预约侧场次和预约
     * @return 支付锁定事实；关联支付不存在为null，调用者检查完整性
     * @throws org.springframework.transaction.IllegalTransactionStateException 没有外层事务
     */
    PaymentLifecycleCase lockByAppointment(Long appointmentOrderId);

    /**
     * 关闭当前事务锁定的未收款支付单，不关闭渠道尝试、不访问渠道。
     * @param payment 本事务lockByAppointment返回的原对象，不接受find/复制/跨事务结果
     * @throws org.springframework.transaction.IllegalTransactionStateException 没有外层事务
     * @throws com.qsx.appoint.common.BusinessException Case无效或UNPAID/PAYING条件更新未命中
     */
    void closeUnpaid(PaymentLifecycleCase payment);
}

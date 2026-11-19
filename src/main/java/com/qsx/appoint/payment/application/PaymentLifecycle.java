package com.qsx.appoint.payment.application;

import java.time.LocalDateTime;

/**
 * 供协调事务锁定支付单并登记付款或关闭。
 */
public interface PaymentLifecycle {
    /**
     * 在已有协调事务中按预约关联锁定支付父记录，获取本事务Case。
     * @param appointmentOrderId 预约单主键，调用前应先锁预约侧场次和预约
     * @return 支付锁定事实；关联支付不存在为null，调用者检查完整性
     * @throws org.springframework.transaction.IllegalTransactionStateException 没有外层事务
     */
    PaymentLifecycleCase lockByAppointment(Long appointmentOrderId);

    /**
     * 关闭当前事务锁定的未付款支付单。
     * @param payment 本事务lockByAppointment返回的原对象，不接受find/复制/跨事务结果
     * @throws org.springframework.transaction.IllegalTransactionStateException 没有外层事务
     * @throws com.qsx.appoint.common.BusinessException Case无效或UNPAID/PAYING条件更新未命中
     */
    void closeUnpaid(PaymentLifecycleCase payment);

    /** 使用当前事务锁定的支付单记录模拟付款。 */
    void markPaid(PaymentLifecycleCase payment, LocalDateTime paidAt);
}

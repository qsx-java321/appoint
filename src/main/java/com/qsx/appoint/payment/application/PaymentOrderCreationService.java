package com.qsx.appoint.payment.application;

/**
 * 预约创建时的支付建单接口，必须加入已有的同库创建事务。
 * <p>金额和截止来自预约暂占结果，不能重新读取当前统一设置计算。
 */
public interface PaymentOrderCreationService {
    /**
     * 在已有预约创建事务中创建UNPAID支付单，失败与预约暂占一并回滚。
     * @param command 可信的预约暂占结果，包括关联账号、费用、截止和创建时间
     * @return 新支付单的稳定结果，不含内部Entity
     * @throws org.springframework.transaction.IllegalTransactionStateException 没有外层事务
     */
    PaymentOrderResult createOrder(CreatePaymentOrderCommand command);
}


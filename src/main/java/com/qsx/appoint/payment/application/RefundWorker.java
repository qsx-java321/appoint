package com.qsx.appoint.payment.application;

public interface RefundWorker {
    /** 幂等执行渠道退款；渠道调用发生在数据库事务之外。 */
    String process(RefundRequestedEvent event);
}

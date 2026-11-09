package com.qsx.appoint.payment.application;

/**
 * 退款事件的渠道执行入口，使用退款单号保证渠道幂等。
 * <p>本地领取与落结果分段提交，渠道调用位于两段事务之间；由监听器在成功返回后ACK。
 */
public interface RefundWorker {
    /**
     * 执行一条退款请求事件，重投时允许再次调用幂等渠道。
     * @param event v1退款请求，含UUID eventId及退款ID/编号
     * @return SUCCEEDED或ALREADY_SUCCEEDED；均表示本地已记录成功，不以普通接收代替成功
     * @throws com.qsx.appoint.common.BusinessException 事件或关联无效、条件更新冲突
     * @throws RuntimeException 渠道失败，记录失败后向上传播供监听器安排重试
     */
    String process(RefundRequestedEvent event);
}

package com.qsx.appoint.payment.application;

/**
 * RefundRequested v1消费者需要的最小标识，不把完整事件正文当数据库写模型。
 * @param eventId 原事件UUID，转发/重放沿用
 * @param eventVersion 契约版本，目前只接受1
 * @param refundId 本地退款单ID
 * @param refundNo 本地退款编号，也是渠道退款幂等依据
 */
public record RefundRequestedEvent(String eventId, int eventVersion, Long refundId, String refundNo) {
}

package com.qsx.appoint.payment.application;

/** RefundRequested v1 消费者所需的稳定标识。 */
public record RefundRequestedEvent(String eventId, int eventVersion, Long refundId, String refundNo) {
}

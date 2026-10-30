package com.qsx.appoint.coordination.application;

import com.qsx.appoint.payment.dto.PaymentResponse;

import java.time.LocalDateTime;

public interface BookingLifecycleCoordinator {
    PaymentResponse simulatePaymentSuccess(Long userId, Long paymentOrderId);
    /** @return 是否已进入异步退款流程。 */
    boolean cancelOwned(Long userId, Long appointmentOrderId);
    boolean expireIfDue(Long appointmentOrderId);
    boolean reconcilePaidPending(Long appointmentOrderId);
    boolean ensureRefundForClosedPaid(Long appointmentOrderId);
    void closeScheduleLocked(Long scheduleId, Long operatorUserId, LocalDateTime now);
}

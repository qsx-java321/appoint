package com.qsx.appoint.coordination.application;


public interface BookingLifecycleCoordinator {
    /** @return 是否已进入异步退款流程。 */
    boolean cancelOwned(Long userId, Long appointmentOrderId);
    boolean expireIfDue(Long appointmentOrderId);
    boolean reconcilePaidPending(Long appointmentOrderId);
    boolean ensureRefundForClosedPaid(Long appointmentOrderId);
}

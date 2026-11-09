package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.PaymentLifecycle;
import com.qsx.appoint.payment.application.PaymentLifecycleCase;
import com.qsx.appoint.payment.application.PaymentRefundOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 取消、过期和对账的事务编排实现，按预约内部锁序→支付父锁连接两个写模块。
 * <p>完整性、归属和入口短路保留在用例中，组合决策委托BookingLifecyclePolicy。
 * 副作用通过完整interface执行；退款与预约释放同事务提交，不直接调用外部支付渠道。
 * @see BookingLifecyclePolicy
 */
@Slf4j
@Service
@RequiredArgsConstructor
class DefaultBookingLifecycleCoordinator implements BookingLifecycleCoordinator {
    private final BookingLifecyclePolicy policy = new BookingLifecyclePolicy();
    private final AppointmentLifecycle appointments;
    private final PaymentLifecycle payments;
    private final PaymentRefundOperations refunds;
    private final Clock appointmentClock;

    /**
     * {@inheritDoc}
     * <p>每次调用由Spring事务代理处理，预约锁和支付锁随同一事务释放。
     */
    @Override
    @Transactional
    public boolean cancelOwned(Long userId, Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!appointment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());

        // 已关闭的重复取消不依赖当前时间；返回值由现有退款状态决定。
        LocalDateTime now = policy.inactive(appointment.status()) ? null : now();
        var decision = policy.decideCancellation(appointment,payment,now);
        return switch (decision.action()) {
            case NO_ACTION -> decision.refundPending();
            case CLOSE_UNPAID -> {
                closePending(appointment,payment,decision.closeReason(),now);
                yield false;
            }
            case CLOSE_PAID_AND_REFUND -> {
                closePaidPending(appointment,payment,decision.closeReason(),decision.refundReason(),now);
                yield true;
            }
            case CANCEL_CONFIRMED_AND_REFUND -> {
                refunds.requestFullRefund(payment.id(),decision.refundReason());
                appointments.cancelConfirmed(appointment,now);
                yield true;
            }
            case REJECT_DEADLINE -> throw new BusinessException(ErrorCode.CANCELLATION_DEADLINE_REACHED);
            case INVALID_STATE -> throw integrityFailure("预约与支付状态组合不一致",appointment.id(),payment.id());
        };
    }

    /**
     * {@inheritDoc}
     * <p>每次调用由Spring事务代理处理，预约锁和支付锁随同一事务释放。
     */
    @Override
    @Transactional
    public boolean expireIfDue(Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) return false;
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT) return false;
        LocalDateTime now = now();
        // 未到期直接短路，不提前要求支付完整性；扫描候选不等于一定应关闭。
        if (now.isBefore(appointment.paymentExpiresAt())) return false;
        PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        return applyResolution(policy.decideExpiration(appointment,payment,now),appointment,payment,now);
    }

    /**
     * {@inheritDoc}
     * <p>每次调用由Spring事务代理处理，预约锁和支付锁随同一事务释放。
     */
    @Override
    @Transactional
    public boolean reconcilePaidPending(Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) return false;
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT) return false;
        PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if (payment.status() != PaymentOrderStatus.PAID) return false;
        LocalDateTime now = now();
        return applyResolution(policy.decidePaidPending(appointment,payment),appointment,payment,now);
    }

    /**
     * {@inheritDoc}
     * <p>每次调用由Spring事务代理处理，预约锁和支付锁随同一事务释放。
     */
    @Override
    @Transactional
    public boolean ensureRefundForClosedPaid(Long appointmentOrderId) {
        AppointmentLifecycleCase appointment = appointments.lock(appointmentOrderId);
        if (appointment == null) return false;
        if (!policy.inactive(appointment.status())) return false;
        PaymentLifecycleCase payment = payments.lockByAppointment(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.id());
        if (!policy.requiresClosedPaidRefund(appointment,payment)) return false;
        refunds.requestFullRefund(payment.id(), RefundReason.RECONCILIATION_CLOSED_PAID);
        return true;
    }

    /**
     * 执行未收款关闭：预约释放/终态/占用删除后，关闭UNPAID或PAYING支付单。
     * <p>两步同事务，支付侧失败会回滚预约释放；不关闭PROCESSING渠道尝试。
     */
    private void closePending(AppointmentLifecycleCase appointment,
                              PaymentLifecycleCase payment, AppointmentCloseReason status, LocalDateTime now) {
        appointments.closePending(appointment, status, now);
        payments.closeUnpaid(payment);
        log.info("预约关闭，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.id(), payment.id(), status.status().name());
    }

    /**
     * 执行待确认已收款的关闭退款，使用Policy给出的关闭原因和退款原因。
     * <p>退款请求与预约关闭参加同一事务，失败不会留下单边资金或库存结果。
     */
    private void closePaidPending(AppointmentLifecycleCase appointment,
                                  PaymentLifecycleCase payment, AppointmentCloseReason status, RefundReason reason,
                                  LocalDateTime now) {
        refunds.requestFullRefund(payment.id(), reason);
        appointments.closePending(appointment, status, now);
        log.info("已支付待确认预约关闭并进入退款，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.id(), payment.id(), status.status().name());
    }

    /**
     * 把过期/已付对账决策映射为完整业务动作，保持入口的boolean语义。
     * @return NO_ACTION为false；确认、未付关闭、迟到退款处理为true，非法组合抛完整性错误
     */
    private boolean applyResolution(BookingLifecyclePolicy.ExpirationDecision decision,
                                    AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                                    LocalDateTime now) {
        return switch (decision) {
            case NO_ACTION -> false;
            case CONFIRM -> {
                appointments.confirm(appointment);
                log.info("对账发现已支付事实并恢复确认，appointmentOrderId={}, paymentOrderId={}",appointment.id(),payment.id());
                yield true;
            }
            case CLOSE_UNPAID -> {
                closePending(appointment,payment,AppointmentCloseReason.PAYMENT_EXPIRED,now);
                yield true;
            }
            case REFUND_LATE_PAYMENT -> {
                closePaidPending(appointment,payment,AppointmentCloseReason.PAYMENT_EXPIRED,
                        RefundReason.APPOINTMENT_NOT_CONFIRMABLE,now);
                yield true;
            }
            case INVALID_STATE -> throw integrityFailure("预约与支付状态组合不一致",appointment.id(),payment.id());
        };
    }

    /**
     * 检查支付存在且关联预约、账号一致，拒绝在错误资金关联上执行补偿。
     * @param expectedPaymentId 期望的支付ID；为null时不额外检查ID等值
     * @throws com.qsx.appoint.common.BusinessException 完整性错误，错误码INTERNAL_ERROR
     */
    private void validateIntegrity(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                                   Long expectedPaymentId) {
        if (payment == null) throw integrityFailure("预约关联的支付单不存在", appointment.id(), expectedPaymentId);
        if (expectedPaymentId != null && !payment.id().equals(expectedPaymentId)) {
            throw integrityFailure("支付单关联关系不一致", appointment.id(), expectedPaymentId);
        }
        if (!payment.appointmentOrderId().equals(appointment.id()) || !payment.userId().equals(appointment.userId())) {
            throw integrityFailure("预约与支付单归属不一致", appointment.id(), payment.id());
        }
    }
    /** 采样服务端业务时间并截断到秒；一次决策复用该时间，避免不同分支跨边界。 */
    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
    /** 统一构造关联完整性错误并记录业务标识，供调用者抛出以触发事务回滚。 */
    private BusinessException integrityFailure(String message, Long appointmentId, Long paymentId) {
        log.error("{}，appointmentOrderId={}, paymentOrderId={}", message, appointmentId, paymentId);
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

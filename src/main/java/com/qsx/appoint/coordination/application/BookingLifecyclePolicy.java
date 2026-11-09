package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.payment.application.PaymentLifecycleCase;
import com.qsx.appoint.payment.domain.PaymentOrderStatus;
import com.qsx.appoint.payment.domain.RefundReason;
import java.time.LocalDateTime;

/**
 * 对预约与支付的组合事实作纯决策，不访问数据库、Clock或渠道，也不产生写入。
 * <p>调用者应先锁定并验证关联；本类返回动作、关闭/退款原因与退款布尔语义。
 * 不同入口保留不同优先级：取消按取消时刻关闭；事件/对账按paidAt判断是否及时收款。
 */
final class BookingLifecyclePolicy {
    enum ExpirationDecision { NO_ACTION, CONFIRM, CLOSE_UNPAID, REFUND_LATE_PAYMENT, INVALID_STATE }
    enum CancellationAction { NO_ACTION, CLOSE_UNPAID, CLOSE_PAID_AND_REFUND, CANCEL_CONFIRMED_AND_REFUND, REJECT_DEADLINE, INVALID_STATE }
    // 决策携带执行所需原因；只有NO_ACTION分支用refundPending表达重复取消的返回值。
    record CancellationDecision(CancellationAction action, AppointmentCloseReason closeReason,
                                RefundReason refundReason, boolean refundPending) { }
    enum PaymentConfirmationDecision { CONFIRM, ALREADY_CONFIRMED, REFUND_CLOSED, EXPIRE_AND_REFUND }

    /**
     * 判断到期候选该如何收敛；非待支付或未到期返回NO_ACTION，已付转按paidAt判断。
     * @param now 用例锁后采样的当前时间，等于截止即到期
     * @return 确认、未付关闭、迟到退款或非法组合等决策，不执行任何写入
     */
    ExpirationDecision decideExpiration(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                                         LocalDateTime now) {
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT || now.isBefore(appointment.paymentExpiresAt())) {
            return ExpirationDecision.NO_ACTION;
        }
        if (payment.status() == PaymentOrderStatus.PAID) return decidePaidPending(appointment,payment);
        return unpaid(payment.status()) ? ExpirationDecision.CLOSE_UNPAID : ExpirationDecision.INVALID_STATE;
    }

    /**
     * 判断已收款待确认预约；paidAt严格早于截止才确认，缺失或等于/晚于截止均退款。
     * <p>不要求当前时间已到期，供扫描和主动对账复用。
     */
    ExpirationDecision decidePaidPending(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment) {
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT || payment.status() != PaymentOrderStatus.PAID) {
            return ExpirationDecision.NO_ACTION;
        }
        return payment.paidAt() != null && payment.paidAt().isBefore(appointment.paymentExpiresAt())
                ? ExpirationDecision.CONFIRM : ExpirationDecision.REFUND_LATE_PAYMENT;
    }

    /**
     * 按当前组合状态与取消时刻选择关闭、退款或拒绝，保留重复取消语义。
     * @param now 非终态必需；终态允许null，因为重复取消只看退款状态
     * @return 含动作、适用原因与是否退款中的决策，未使用的原因字段为null
     */
    CancellationDecision decideCancellation(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment,
                                             LocalDateTime now) {
        if (inactive(appointment.status())) {
            boolean pending = payment.status() == PaymentOrderStatus.REFUND_PENDING;
            boolean closed = pending || payment.status() == PaymentOrderStatus.REFUNDED || payment.status() == PaymentOrderStatus.CLOSED;
            return new CancellationDecision(closed ? CancellationAction.NO_ACTION : CancellationAction.INVALID_STATE,null,null,pending);
        }
        if (appointment.status() == AppointmentStatus.CONFIRMED) {
            if (payment.status() != PaymentOrderStatus.PAID) return invalidCancellation();
            LocalDateTime deadline = LocalDateTime.of(appointment.appointmentDate(),appointment.startTime()).minusHours(24);
            if (!now.isBefore(deadline)) return new CancellationDecision(CancellationAction.REJECT_DEADLINE,null,null,false);
            return new CancellationDecision(CancellationAction.CANCEL_CONFIRMED_AND_REFUND,null,RefundReason.APPOINTMENT_CANCELLED,true);
        }
        AppointmentCloseReason reason = now.isBefore(appointment.paymentExpiresAt())
                ? AppointmentCloseReason.USER_CANCELLED : AppointmentCloseReason.PAYMENT_EXPIRED;
        if (payment.status() == PaymentOrderStatus.PAID) {
            return new CancellationDecision(CancellationAction.CLOSE_PAID_AND_REFUND,reason,RefundReason.APPOINTMENT_CANCELLED,true);
        }
        return unpaid(payment.status()) ? new CancellationDecision(CancellationAction.CLOSE_UNPAID,reason,null,false) : invalidCancellation();
    }

    /**
     * 按已验证的成功事件判断预约动作；已确认优先幂等，已关闭只退款。
     * <p>待支付时仅比较event.paidAt与预约截止；等于截止走EXPIRE_AND_REFUND。
     */
    PaymentConfirmationDecision decidePaymentConfirmation(AppointmentLifecycleCase appointment, PaymentSucceededEvent event) {
        if (appointment.status() == AppointmentStatus.CONFIRMED) return PaymentConfirmationDecision.ALREADY_CONFIRMED;
        if (appointment.status() != AppointmentStatus.PENDING_PAYMENT) return PaymentConfirmationDecision.REFUND_CLOSED;
        return event.paidAt().isBefore(appointment.paymentExpiresAt())
                ? PaymentConfirmationDecision.CONFIRM : PaymentConfirmationDecision.EXPIRE_AND_REFUND;
    }
    /** 判断是否属于已关闭且仍PAID的漏退款候选；不读取退款表或保证已创建退款。 */
    boolean requiresClosedPaidRefund(AppointmentLifecycleCase appointment, PaymentLifecycleCase payment) {
        return inactive(appointment.status()) && payment.status() == PaymentOrderStatus.PAID;
    }
    /** 识别取消、过期及历史停诊取消终态，不让历史状态恢复成有效预约。 */
    boolean inactive(AppointmentStatus status) {
        return status == AppointmentStatus.CANCELLED || status == AppointmentStatus.EXPIRED || status == AppointmentStatus.CANCELLED_BY_HOSPITAL;
    }
    /** 将UNPAID与PAYING归为可关闭的未收款支付状态，不包含PAID或退款状态。 */
    private boolean unpaid(PaymentOrderStatus status) { return status == PaymentOrderStatus.UNPAID || status == PaymentOrderStatus.PAYING; }
    /** 构造非法取消组合决策，由用例统一转换为完整性异常。 */
    private CancellationDecision invalidCancellation() { return new CancellationDecision(CancellationAction.INVALID_STATE,null,null,false); }
}

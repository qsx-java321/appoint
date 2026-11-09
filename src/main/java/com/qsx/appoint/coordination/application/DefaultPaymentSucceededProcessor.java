package com.qsx.appoint.coordination.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.payment.domain.RefundReason;
import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.persistence.CoordinationInboxEventMapper;
import com.qsx.appoint.payment.application.PaymentRefundOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 支付成功事件的本地事务实现：校验→Inbox→预约锁定→Policy→完整动作。
 * <p>事件是已提交资金事实；普通确认不重读支付单。重复消息与重复事实分开处理：
 * 相同eventId由Inbox去重，新eventId遇已确认预约也不重复确认或分配序号。
 * @see BookingLifecyclePolicy
 */
@Service
@RequiredArgsConstructor
class DefaultPaymentSucceededProcessor implements PaymentSucceededProcessor {
    private static final String CONSUMER = "appointment-payment-succeeded-v1";

    private final BookingLifecyclePolicy policy = new BookingLifecyclePolicy();
    private final CoordinationInboxEventMapper inboxMapper;
    private final AppointmentLifecycle appointments;
    private final PaymentRefundOperations refunds;
    private final Clock appointmentClock;

    /**
     * {@inheritDoc}
     * <p>任何后续释放或退款请求失败会回滚Inbox，使消息重试有机会重新完成整笔处理。
     */
    @Override
    @Transactional
    public String process(PaymentSucceededEvent event) {
        validate(event);
        // Inbox登记和业务变更同事务；后续失败会撤销本次登记，成功后重投才被判为重复。
        if (inboxMapper.insertIgnore(CONSUMER, event.eventId(), "PaymentSucceeded", now()) == 0) {
            return "DUPLICATE";
        }

        AppointmentLifecycleCase appointment = appointments.lock(event.appointmentOrderId());
        if (appointment == null || !appointment.id().equals(event.appointmentOrderId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // 以已提交的成功事件判断资格；正常确认不重读资金，已确认预约优先幂等短路。
        return switch (policy.decidePaymentConfirmation(appointment,event)) {
            case ALREADY_CONFIRMED -> "ALREADY_CONFIRMED";
            case REFUND_CLOSED -> {
                refunds.requestFullRefund(event.paymentOrderId(),RefundReason.APPOINTMENT_NOT_CONFIRMABLE);
                yield "REFUND_REQUIRED";
            }
            case EXPIRE_AND_REFUND -> {
                // 迟到收款必须同时释放暂占并确保退款；任一步失败都回滚，避免残留占号。
                appointments.closePending(appointment,AppointmentCloseReason.PAYMENT_EXPIRED,now());
                refunds.requestFullRefund(event.paymentOrderId(),RefundReason.APPOINTMENT_NOT_CONFIRMABLE);
                yield "REFUND_REQUIRED";
            }
            case CONFIRM -> {
                appointments.confirm(appointment);
                yield "CONFIRMED";
            }
        };
    }

    /**
     * 对业务入口再次校验必填字段、v1、UUID、正金额和CNY，保护非Rabbit调用者。
     * <p>契约校验先于Inbox登记；永久无效消息不能留下已处理凭证。
     * @throws com.qsx.appoint.common.BusinessException 契约不满足，错误码VALIDATION_ERROR
     */
    private void validate(PaymentSucceededEvent event) {
        try {
            if (event == null || event.eventVersion() != 1 || event.eventId() == null
                    || event.occurredAt() == null || event.paymentOrderId() == null
                    || event.appointmentOrderId() == null || event.paymentAttemptId() == null
                    || event.amount() == null || event.amount().signum() <= 0
                    || !"CNY".equals(event.currency()) || event.channel() == null
                    || event.paidAt() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            UUID.fromString(event.eventId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
    /** 读取截断到秒的服务端处理时间，用于Inbox/关闭记录；不替代事件paidAt判断收款资格。 */
    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

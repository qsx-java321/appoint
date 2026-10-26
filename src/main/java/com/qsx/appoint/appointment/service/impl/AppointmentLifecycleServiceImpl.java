package com.qsx.appoint.appointment.service.impl;

import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.dto.PaymentResponse;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentLifecycleServiceImpl implements AppointmentLifecycleService {
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final ScheduleMapper scheduleMapper;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public PaymentResponse simulatePaymentSuccess(Long userId, Long paymentId) {
        PaymentOrder lookup = paymentOrderMapper.selectByIdForLookup(paymentId);
        if (lookup == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);

        AppointmentOrder appointment = appointmentOrderMapper.selectByIdForUpdate(lookup.getAppointmentOrderId());
        if (appointment == null) throw integrityFailure("支付单关联的预约不存在", null, paymentId);
        PaymentOrder payment = paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointment.getId());
        validateIntegrity(appointment, payment, paymentId);
        if (!appointment.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);

        if ("CONFIRMED".equals(appointment.getStatus()) && "PAID".equals(payment.getStatus())) {
            return response(payment, appointment);
        }
        if ("EXPIRED".equals(appointment.getStatus())) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        if ("CANCELLED".equals(appointment.getStatus())) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        if (!"PENDING_PAYMENT".equals(appointment.getStatus()) || !"UNPAID".equals(payment.getStatus())) {
            throw integrityFailure("预约与支付状态组合不一致", appointment.getId(), paymentId);
        }

        LocalDateTime now = now();
        if (!now.isBefore(appointment.getPaymentExpiresAt())) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
        requireOne(paymentOrderMapper.payIfUnpaid(payment.getId()), "支付单状态推进失败", appointment.getId(), paymentId);
        requireOne(appointmentOrderMapper.confirmIfPending(appointment.getId()), "预约单状态推进失败", appointment.getId(), paymentId);
        requireOne(scheduleMapper.bookIfHeld(appointment.getScheduleId()), "号源确认失败", appointment.getId(), paymentId);
        payment.setStatus("PAID");
        appointment.setStatus("CONFIRMED");
        log.info("模拟支付成功，paymentOrderId={}, appointmentOrderId={}", paymentId, appointment.getId());
        return response(payment, appointment);
    }

    @Override
    @Transactional
    public void cancelOwned(Long userId, Long appointmentOrderId) {
        AppointmentOrder appointment = appointmentOrderMapper.selectByIdForUpdate(appointmentOrderId);
        if (appointment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!appointment.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        PaymentOrder payment = paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.getId());

        if (isClosed(appointment.getStatus())) {
            if (!"CLOSED".equals(payment.getStatus())) {
                throw integrityFailure("已关闭预约的支付单状态不一致", appointmentOrderId, payment.getId());
            }
            return;
        }
        if ("CONFIRMED".equals(appointment.getStatus())) {
            if (!"PAID".equals(payment.getStatus())) {
                throw integrityFailure("已确认预约的支付单状态不一致", appointmentOrderId, payment.getId());
            }
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        if (!"PENDING_PAYMENT".equals(appointment.getStatus()) || !"UNPAID".equals(payment.getStatus())) {
            throw integrityFailure("预约与支付状态组合不一致", appointmentOrderId, payment.getId());
        }
        LocalDateTime now = now();
        String targetStatus = now.isBefore(appointment.getPaymentExpiresAt()) ? "CANCELLED" : "EXPIRED";
        close(appointment, payment, targetStatus, now);
    }

    @Override
    @Transactional
    public boolean expireIfDue(Long appointmentOrderId) {
        AppointmentOrder appointment = appointmentOrderMapper.selectByIdForUpdate(appointmentOrderId);
        if (appointment == null || !"PENDING_PAYMENT".equals(appointment.getStatus())) return false;
        LocalDateTime now = now();
        if (now.isBefore(appointment.getPaymentExpiresAt())) return false;
        PaymentOrder payment = paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.getId());
        if (!"UNPAID".equals(payment.getStatus())) {
            throw integrityFailure("待支付预约的支付单状态不一致", appointmentOrderId, payment.getId());
        }
        close(appointment, payment, "EXPIRED", now);
        return true;
    }

    private void close(AppointmentOrder appointment, PaymentOrder payment, String targetStatus, LocalDateTime now) {
        requireOne(appointmentOrderMapper.closeIfPending(appointment.getId(), targetStatus, now),
                "预约关闭失败", appointment.getId(), payment.getId());
        requireOne(paymentOrderMapper.closeIfUnpaid(payment.getId()),
                "支付单关闭失败", appointment.getId(), payment.getId());
        requireOne(scheduleMapper.releaseIfHeld(appointment.getScheduleId()),
                "号源释放失败", appointment.getId(), payment.getId());
        log.info("预约关闭，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.getId(), payment.getId(), targetStatus);
    }

    private void validateIntegrity(AppointmentOrder appointment, PaymentOrder payment, Long expectedPaymentId) {
        if (payment == null) throw integrityFailure("预约关联的支付单不存在", appointment.getId(), expectedPaymentId);
        if (expectedPaymentId != null && !payment.getId().equals(expectedPaymentId)) {
            throw integrityFailure("支付单关联关系不一致", appointment.getId(), expectedPaymentId);
        }
        if (!payment.getAppointmentOrderId().equals(appointment.getId())
                || !payment.getUserId().equals(appointment.getUserId())) {
            throw integrityFailure("预约与支付单归属不一致", appointment.getId(), payment.getId());
        }
    }

    private void requireOne(int updated, String message, Long appointmentId, Long paymentId) {
        if (updated != 1) throw integrityFailure(message, appointmentId, paymentId);
    }

    private BusinessException integrityFailure(String message, Long appointmentId, Long paymentId) {
        log.error("{}，appointmentOrderId={}, paymentOrderId={}", message, appointmentId, paymentId);
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    private boolean isClosed(String status) {
        return "CANCELLED".equals(status) || "EXPIRED".equals(status);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private PaymentResponse response(PaymentOrder payment, AppointmentOrder appointment) {
        return new PaymentResponse(payment.getId(), payment.getPaymentNo(), payment.getStatus(),
                appointment.getId(), appointment.getStatus());
    }
}

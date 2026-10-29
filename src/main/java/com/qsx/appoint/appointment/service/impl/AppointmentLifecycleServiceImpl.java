package com.qsx.appoint.appointment.service.impl;

import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentActiveClaimMapper;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import com.qsx.appoint.catalog.entity.Schedule;
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
    private final AppointmentActiveClaimMapper activeClaimMapper;
    private final ScheduleMapper scheduleMapper;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public PaymentResponse simulatePaymentSuccess(Long userId, Long paymentId) {
        PaymentOrder lookupPayment = paymentOrderMapper.selectByIdForLookup(paymentId);
        if (lookupPayment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        AppointmentOrder lookupAppointment = appointmentOrderMapper.selectByIdForLookup(
                lookupPayment.getAppointmentOrderId());
        if (lookupAppointment == null) throw integrityFailure("支付单关联的预约不存在", null, paymentId);
        Schedule schedule = lockNewSchedule(lookupAppointment.getScheduleId());
        AppointmentOrder appointment = appointmentOrderMapper.selectByIdForUpdate(lookupAppointment.getId());
        PaymentOrder payment = paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointment.getId());
        validateIntegrity(appointment, payment, paymentId);
        if (!appointment.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);

        if ("CONFIRMED".equals(appointment.getStatus()) && "PAID".equals(payment.getStatus())) {
            return response(payment, appointment);
        }
        if ("EXPIRED".equals(appointment.getStatus())) throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        if (isInactive(appointment.getStatus())) throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        requirePendingUnpaid(appointment, payment, paymentId);

        LocalDateTime now = now();
        if (!now.isBefore(appointment.getPaymentExpiresAt())) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
        if (!"OPEN".equals(schedule.getStatus())) throw new BusinessException(ErrorCode.SCHEDULE_CLOSED);
        Integer visitNumber = schedule.getNextVisitNumber();
        requireOne(scheduleMapper.confirmOne(schedule.getId(), visitNumber),
                "库存确认失败", appointment.getId(), paymentId);
        requireOne(paymentOrderMapper.payIfUnpaid(payment.getId()), "支付单状态推进失败", appointment.getId(), paymentId);
        requireOne(appointmentOrderMapper.confirmIfPending(appointment.getId(), visitNumber),
                "预约单状态推进失败", appointment.getId(), paymentId);
        payment.setStatus("PAID");
        appointment.setStatus("CONFIRMED");
        appointment.setVisitNumber(visitNumber);
        log.info("模拟支付成功，paymentOrderId={}, appointmentOrderId={}, visitNumber={}",
                paymentId, appointment.getId(), visitNumber);
        return response(payment, appointment);
    }

    @Override
    @Transactional
    public void cancelOwned(Long userId, Long appointmentOrderId) {
        AppointmentOrder lookup = appointmentOrderMapper.selectByIdForLookup(appointmentOrderId);
        if (lookup == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Schedule schedule = lockNewSchedule(lookup.getScheduleId());
        AppointmentOrder appointment = appointmentOrderMapper.selectByIdForUpdate(appointmentOrderId);
        if (!appointment.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        PaymentOrder payment = paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.getId());

        if (isInactive(appointment.getStatus())) {
            if (!("CLOSED".equals(payment.getStatus()) || "REFUNDED".equals(payment.getStatus()))) {
                throw integrityFailure("已关闭预约的支付单状态不一致", appointmentOrderId, payment.getId());
            }
            return;
        }
        LocalDateTime now = now();
        if ("CONFIRMED".equals(appointment.getStatus())) {
            if (!"PAID".equals(payment.getStatus())) {
                throw integrityFailure("已确认预约的支付单状态不一致", appointmentOrderId, payment.getId());
            }
            LocalDateTime cancellationDeadline = LocalDateTime.of(
                    appointment.getAppointmentDate(), appointment.getStartTime()).minusHours(24);
            if (!now.isBefore(cancellationDeadline)) {
                throw new BusinessException(ErrorCode.CANCELLATION_DEADLINE_REACHED);
            }
            requireOne(scheduleMapper.releaseConfirmedOne(schedule.getId()),
                    "已确认库存释放失败", appointmentOrderId, payment.getId());
            requireOne(paymentOrderMapper.refundIfPaid(payment.getId()),
                    "模拟退款状态推进失败", appointmentOrderId, payment.getId());
            requireOne(appointmentOrderMapper.cancelIfConfirmed(appointmentOrderId, now),
                    "已确认预约取消失败", appointmentOrderId, payment.getId());
            requireClaimDeleted(appointmentOrderId, payment.getId());
            return;
        }
        requirePendingUnpaid(appointment, payment, payment.getId());
        String targetStatus = now.isBefore(appointment.getPaymentExpiresAt()) ? "CANCELLED" : "EXPIRED";
        closePending(schedule, appointment, payment, targetStatus, now);
    }

    @Override
    @Transactional
    public boolean expireIfDue(Long appointmentOrderId) {
        AppointmentOrder lookup = appointmentOrderMapper.selectByIdForLookup(appointmentOrderId);
        if (lookup == null) return false;
        Schedule schedule = lockNewSchedule(lookup.getScheduleId());
        AppointmentOrder appointment = appointmentOrderMapper.selectByIdForUpdate(appointmentOrderId);
        if (!"PENDING_PAYMENT".equals(appointment.getStatus())) return false;
        LocalDateTime now = now();
        if (now.isBefore(appointment.getPaymentExpiresAt())) return false;
        PaymentOrder payment = paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointmentOrderId);
        validateIntegrity(appointment, payment, payment == null ? null : payment.getId());
        requirePendingUnpaid(appointment, payment, payment.getId());
        closePending(schedule, appointment, payment, "EXPIRED", now);
        return true;
    }

    @Override
    @Transactional
    public void closeScheduleLocked(Schedule schedule, Long operatorUserId, LocalDateTime now) {
        requireOne(scheduleMapper.closeIfOpen(schedule.getId(), now, operatorUserId),
                "排班关闭失败", null, null);
        for (AppointmentOrder appointment : appointmentOrderMapper.selectPendingByScheduleForUpdate(schedule.getId())) {
            PaymentOrder payment = paymentOrderMapper.selectByAppointmentOrderIdForUpdate(appointment.getId());
            validateIntegrity(appointment, payment, payment == null ? null : payment.getId());
            requirePendingUnpaid(appointment, payment, payment.getId());
            String target = now.isBefore(appointment.getPaymentExpiresAt())
                    ? "CANCELLED_BY_HOSPITAL" : "EXPIRED";
            closePending(schedule, appointment, payment, target, now);
        }
        log.info("排班关闭，scheduleId={}, operatorUserId={}", schedule.getId(), operatorUserId);
    }

    private void closePending(Schedule schedule, AppointmentOrder appointment, PaymentOrder payment,
                              String targetStatus, LocalDateTime now) {
        requireOne(scheduleMapper.releaseHeldOne(schedule.getId()),
                "暂占库存释放失败", appointment.getId(), payment.getId());
        requireOne(appointmentOrderMapper.closeIfPending(appointment.getId(), targetStatus, now),
                "预约关闭失败", appointment.getId(), payment.getId());
        requireOne(paymentOrderMapper.closeIfUnpaid(payment.getId()),
                "支付单关闭失败", appointment.getId(), payment.getId());
        requireClaimDeleted(appointment.getId(), payment.getId());
        log.info("预约关闭，appointmentOrderId={}, paymentOrderId={}, status={}",
                appointment.getId(), payment.getId(), targetStatus);
    }

    private Schedule lockNewSchedule(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectByIdForUpdate(scheduleId);
        if (schedule == null || schedule.getSessionType() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return schedule;
    }

    private void requirePendingUnpaid(AppointmentOrder appointment, PaymentOrder payment, Long paymentId) {
        if (!"PENDING_PAYMENT".equals(appointment.getStatus()) || !"UNPAID".equals(payment.getStatus())) {
            throw integrityFailure("预约与支付状态组合不一致", appointment.getId(), paymentId);
        }
    }

    private void requireClaimDeleted(Long appointmentId, Long paymentId) {
        requireOne(activeClaimMapper.deleteByAppointmentOrderId(appointmentId),
                "有效预约占用释放失败", appointmentId, paymentId);
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

    private boolean isInactive(String status) {
        return "CANCELLED".equals(status) || "EXPIRED".equals(status)
                || "CANCELLED_BY_HOSPITAL".equals(status);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private PaymentResponse response(PaymentOrder payment, AppointmentOrder appointment) {
        return new PaymentResponse(payment.getId(), payment.getPaymentNo(), payment.getStatus(),
                appointment.getId(), appointment.getStatus(), appointment.getVisitNumber());
    }
}

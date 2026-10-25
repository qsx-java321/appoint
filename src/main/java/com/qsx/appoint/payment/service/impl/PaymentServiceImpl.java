package com.qsx.appoint.payment.service.impl;

import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.dto.PaymentResponse;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模拟支付只记录安全的订单标识，不接入真实第三方支付。
 * 条件更新使重复请求不会重复推进预约和号源状态。
 */
@Slf4j @Service @RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentOrderMapper paymentOrderMapper;
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final ScheduleMapper scheduleMapper;

    @Override
    @Transactional
    public PaymentResponse simulateSuccess(Long userId, Long paymentId) {
        PaymentOrder payment = paymentOrderMapper.selectById(paymentId);
        if (payment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!payment.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);

        AppointmentOrder appointment = appointmentOrderMapper.selectById(payment.getAppointmentOrderId());
        if ("PAID".equals(payment.getStatus())) {
            return response(payment, appointment);
        }
        if (paymentOrderMapper.payIfUnpaid(paymentId) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        if (appointmentOrderMapper.confirmIfPending(appointment.getId()) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        if (scheduleMapper.bookIfHeld(appointment.getScheduleId()) != 1) {
            throw new BusinessException(ErrorCode.ORDER_STATE_ERROR);
        }
        log.info("模拟支付成功，paymentOrderId={}, appointmentOrderId={}", paymentId, appointment.getId());
        payment.setStatus("PAID");
        appointment.setStatus("CONFIRMED");
        return response(payment, appointment);
    }

    private PaymentResponse response(PaymentOrder payment, AppointmentOrder appointment) {
        return new PaymentResponse(payment.getId(), payment.getPaymentNo(), payment.getStatus(), appointment.getId(), appointment.getStatus());
    }
}

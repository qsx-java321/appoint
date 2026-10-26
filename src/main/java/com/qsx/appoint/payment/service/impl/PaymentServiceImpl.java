package com.qsx.appoint.payment.service.impl;

import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import com.qsx.appoint.payment.dto.PaymentResponse;
import com.qsx.appoint.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模拟支付只记录安全的订单标识，不接入真实第三方支付。
 * 条件更新使重复请求不会重复推进预约和号源状态。
 */
@Service @RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final AppointmentLifecycleService lifecycleService;

    @Override
    public PaymentResponse simulateSuccess(Long userId, Long paymentId) {
        return lifecycleService.simulatePaymentSuccess(userId, paymentId);
    }
}

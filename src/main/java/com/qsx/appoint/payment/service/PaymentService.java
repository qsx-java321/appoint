package com.qsx.appoint.payment.service;

import com.qsx.appoint.payment.dto.PaymentResponse;

/** 支付服务只提供演示支付成功这一条项目需要的路径。 */
public interface PaymentService {
    PaymentResponse simulateSuccess(Long userId, Long paymentId);
}

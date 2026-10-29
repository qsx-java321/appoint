package com.qsx.appoint.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模拟支付后返回支付与关联预约的最终状态。 */
@Getter @AllArgsConstructor
public class PaymentResponse {
    private final Long id;
    private final String paymentNo;
    private final String paymentStatus;
    private final Long appointmentOrderId;
    private final String appointmentStatus;
    private final Integer visitNumber;

    public PaymentResponse(Long id, String paymentNo, String paymentStatus,
                           Long appointmentOrderId, String appointmentStatus) {
        this(id, paymentNo, paymentStatus, appointmentOrderId, appointmentStatus, null);
    }
}

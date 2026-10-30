package com.qsx.appoint.payment.controller;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.common.Result;
import com.qsx.appoint.payment.application.PaymentNotificationIngress;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/payment-notifications")
@RequiredArgsConstructor
public class PaymentNotificationController {
    private final PaymentNotificationIngress ingress;

    @PostMapping("/{channel}")
    public Result<PaymentNotificationResponse> notify(@PathVariable String channel,
                                                      @RequestBody RawPaymentNotification raw) {
        PaymentChannel paymentChannel = parseChannel(channel);
        return Result.success(ingress.handle(paymentChannel, raw));
    }

    private PaymentChannel parseChannel(String value) {
        try {
            return PaymentChannel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_UNAVAILABLE);
        }
    }
}

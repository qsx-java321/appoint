package com.qsx.appoint.payment.controller;

import com.qsx.appoint.common.Result;
import com.qsx.appoint.payment.dto.PaymentResponse;
import com.qsx.appoint.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 演示支付接口，仅用于推动订单状态，不包含真实支付渠道。 */
@RestController @RequestMapping("/api/v1/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    @PostMapping("/{id}/simulate-success")
    public Result<PaymentResponse> simulateSuccess(Authentication authentication, @PathVariable Long id) { return Result.success(paymentService.simulateSuccess((Long) authentication.getPrincipal(), id)); }
}

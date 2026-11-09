package com.qsx.appoint.payment.controller;

import com.qsx.appoint.common.Result;
import com.qsx.appoint.payment.application.PaymentAttemptService;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;
import com.qsx.appoint.payment.dto.StartPaymentAttemptRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 本人支付尝试HTTP入口，委托PaymentAttemptService发起渠道会话。
 * <p>202表示尝试建立，最终资金与预约结果通过通知和查询确认。
 */
@RestController @RequestMapping("/api/v1/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentAttemptService paymentAttemptService;

    /**
     * 为路径指定的本人支付单发起渠道会话。
     * @param id 支付单ID，不是预约ID
     * @param request 支付渠道枚举请求
     * @return HTTP202与尝试/checkoutUrl；后续由通知完成收款
     */
    @PostMapping("/{id}/attempts")
    public ResponseEntity<Result<PaymentAttemptResponse>> startAttempt(
            Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody StartPaymentAttemptRequest request) {
        PaymentAttemptResponse response = paymentAttemptService.startAttempt(
                (Long) authentication.getPrincipal(), id, request.getChannel());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(response));
    }
}

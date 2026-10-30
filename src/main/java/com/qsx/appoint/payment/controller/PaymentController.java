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

@RestController @RequestMapping("/api/v1/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentAttemptService paymentAttemptService;

    @PostMapping("/{id}/attempts")
    public ResponseEntity<Result<PaymentAttemptResponse>> startAttempt(
            Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody StartPaymentAttemptRequest request) {
        PaymentAttemptResponse response = paymentAttemptService.startAttempt(
                (Long) authentication.getPrincipal(), id, request.getChannel());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(response));
    }
}

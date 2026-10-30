package com.qsx.appoint.payment.dto;

import com.qsx.appoint.payment.domain.PaymentChannel;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StartPaymentAttemptRequest {
    @NotNull
    private PaymentChannel channel;
}

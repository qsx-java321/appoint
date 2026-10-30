package com.qsx.appoint.coordination.application;

public interface PaymentSucceededProcessor {
    String process(PaymentSucceededEvent event);
}

package com.qsx.appoint.payment.application;

public interface PaymentOrderCreationService {
    PaymentOrderResult createOrder(CreatePaymentOrderCommand command);
}


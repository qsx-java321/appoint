package com.qsx.appoint.payment.application.archfixture;

public interface LeakingPaymentContract {
    com.qsx.appoint.payment.entity.PaymentOrder direct();
    java.util.List<com.qsx.appoint.payment.entity.PaymentOrder> nested();
}

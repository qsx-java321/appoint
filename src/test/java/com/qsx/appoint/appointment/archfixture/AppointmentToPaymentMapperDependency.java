package com.qsx.appoint.appointment.archfixture;

import com.qsx.appoint.payment.mapper.PaymentOrderMapper;

/** 仅用于证明模块规则会拒绝新增的 appointment → payment Mapper 依赖。 */
public class AppointmentToPaymentMapperDependency {
    private PaymentOrderMapper paymentOrderMapper;
}

package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 从预约暂占快照创建唯一对应的UNPAID支付单。
 * <p>MANDATORY保证支付建单与预约/库存同库原子提交，不在本类查询或写入预约表。
 */
@Service
@RequiredArgsConstructor
class PaymentOrderCreationServiceImpl implements PaymentOrderCreationService {
    private final PaymentOrderMapper mapper;
    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentOrderResult createOrder(CreatePaymentOrderCommand command) {
        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo("PAY" + UUID.randomUUID().toString().replace("-", ""));
        payment.setAppointmentOrderId(command.appointmentOrderId());
        payment.setUserId(command.userId());
        payment.setStatus("UNPAID");
        payment.setAmount(command.amount());
        payment.setCurrency(command.currency());
        payment.setExpiresAt(command.expiresAt());
        payment.setCreatedAt(command.createdAt());
        payment.setUpdatedAt(command.createdAt());
        mapper.insert(payment);
        return new PaymentOrderResult(payment.getId(), payment.getPaymentNo(), payment.getStatus(),
                payment.getAmount(), payment.getCurrency(), payment.getExpiresAt());
    }
}


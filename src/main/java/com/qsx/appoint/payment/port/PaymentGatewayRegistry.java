package com.qsx.appoint.payment.port;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayRegistry {
    private final Map<PaymentChannel, PaymentGateway> gateways;

    public PaymentGatewayRegistry(List<PaymentGateway> registered) {
        EnumMap<PaymentChannel, PaymentGateway> byChannel = new EnumMap<>(PaymentChannel.class);
        for (PaymentGateway gateway : registered) {
            if (byChannel.put(gateway.channel(), gateway) != null) {
                throw new IllegalStateException("支付渠道重复注册: " + gateway.channel());
            }
        }
        this.gateways = Map.copyOf(byChannel);
    }

    public PaymentGateway require(PaymentChannel channel) {
        PaymentGateway gateway = gateways.get(channel);
        if (gateway == null) throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_UNAVAILABLE);
        return gateway;
    }
}

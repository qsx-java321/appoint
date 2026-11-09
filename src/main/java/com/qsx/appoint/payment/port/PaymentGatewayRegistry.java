package com.qsx.appoint.payment.port;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 按稳定渠道枚举定位Spring注册的Gateway Adapter。
 * <p>启动时拒绝同渠道重复实现；枚举有值不代表当前环境已经接入该渠道。
 */
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

    /**
     * 返回指定渠道Adapter；未注册不能以枚举存在推断可用。
     * @param channel 期望渠道
     * @return 已注册Gateway，不为null
     * @throws com.qsx.appoint.common.BusinessException 未注册，错误码PAYMENT_CHANNEL_UNAVAILABLE
     */
    public PaymentGateway require(PaymentChannel channel) {
        PaymentGateway gateway = gateways.get(channel);
        if (gateway == null) throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_UNAVAILABLE);
        return gateway;
    }
}

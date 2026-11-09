package com.qsx.appoint.payment.controller;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.common.Result;
import com.qsx.appoint.payment.application.PaymentNotificationIngress;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 渠道通知HTTP入口，不依赖Bearer登录，必须经过Ingress的渠道验签。
 * <p>HTTP200只表示返回处理摘要；需查看processingResult，不能把拒绝审计当作支付成功。
 */
@RestController
@RequestMapping("/api/v1/payment-notifications")
@RequiredArgsConstructor
public class PaymentNotificationController {
    private final PaymentNotificationIngress ingress;

    /**
     * 解析渠道并委托Ingress验签及本地事务处理。
     * @param channel 路径渠道名，忽略首尾空白并按大写枚举解析
     * @param raw 原始签名通知
     * @return 处理摘要，审计拒绝也可能正常HTTP200返回
     */
    @PostMapping("/{channel}")
    public Result<PaymentNotificationResponse> notify(@PathVariable String channel,
                                                      @RequestBody RawPaymentNotification raw) {
        PaymentChannel paymentChannel = parseChannel(channel);
        return Result.success(ingress.handle(paymentChannel, raw));
    }
    /** 解析稳定渠道枚举，非法渠道转PAYMENT_CHANNEL_UNAVAILABLE，不暴露解析异常。 */
    private PaymentChannel parseChannel(String value) {
        try {
            return PaymentChannel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_UNAVAILABLE);
        }
    }
}

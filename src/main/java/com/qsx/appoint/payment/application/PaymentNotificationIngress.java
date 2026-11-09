package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.port.RawPaymentNotification;

/**
 * 未经信任的渠道通知入口：先由注册Gateway验签，再交事务服务处理资金事实。
 * <p>Controller及Mock异步分发均应经过此入口，不能把原始通知直接交给业务写入。
 */
public interface PaymentNotificationIngress {
    /**
     * 接收原始通知并完成渠道验签，再推进本地资金处理。
     * @param channel 通知所属渠道，决定使用哪个Gateway验签
     * @param notification 原始通知，签名和内容均不应在验签前视作可信
     * @return 处理摘要；拒绝类审计结果也可能正常返回，须检查processingResult
     * @throws com.qsx.appoint.common.BusinessException 渠道不可用、签名无效、幂等冲突或业务处理失败
     */
    PaymentNotificationResponse handle(PaymentChannel channel, RawPaymentNotification notification);
}

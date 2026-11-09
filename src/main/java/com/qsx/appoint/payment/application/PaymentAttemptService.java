package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;

/**
 * 用户发起支付尝试的用例入口，连接本地尝试事务和事务外渠道下单。
 * <p>一次成功返回表示渠道会话已建立，不表示资金已收款或预约已确认。
 */
public interface PaymentAttemptService {
    /**
     * 为本人未到期UNPAID支付单发起一次渠道支付尝试。
     * @param userId 当前账号ID，不能为他人支付单发起尝试
     * @param paymentOrderId 支付单主键，不是预约单或attemptID
     * @param channel 已注册且可用的渠道；本地dev/test使用MOCK
     * @return 渠道会话与尝试摘要，通常PAYING/PROCESSING，资金结果由通知异步决定
     * @throws com.qsx.appoint.common.BusinessException 非本人、到期、历史单、状态不符、已有活跃尝试或渠道不可用
     */
    PaymentAttemptResponse startAttempt(Long userId, Long paymentOrderId, PaymentChannel channel);
}

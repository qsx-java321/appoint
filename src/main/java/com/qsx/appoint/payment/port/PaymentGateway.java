package com.qsx.appoint.payment.port;

import com.qsx.appoint.payment.domain.PaymentChannel;

/**
 * 支付渠道port，隔离渠道SDK与内部支付写模型。
 * <p>下单和退款由用例在数据库事务外调用；实现需明确按attemptNo/refundNo幂等。
 * 当前仅MockPay在dev/test注册，真实微信与支付宝实现尚未接入。
 */
public interface PaymentGateway {
    /**
     * 返回此Adapter支持的稳定渠道枚举，注册器据此建立唯一映射。
     * @return 渠道标识，不是SDK实例名
     */
    PaymentChannel channel();

    /**
     * 按attemptNo幂等请求渠道下单，调用者应在本地T1事务提交后调用。
     * @param request 本地已保存的尝试、费用和截止快照
     * @return 渠道会话；当前发起用例只接受PROCESSING
     * @throws com.qsx.appoint.common.BusinessException 请求不合法、同幂等号异内容或渠道业务拒绝
     */
    GatewayPaymentSession createPayment(GatewayPaymentRequest request);

    /**
     * 关闭指定渠道订单；本地支付关闭不自动调用本方法。
     * @param request 本地支付/尝试编号与渠道订单号
     * @throws com.qsx.appoint.common.BusinessException 关联不符或状态不允许关闭
     */
    void closePayment(GatewayCloseRequest request);

    /**
     * 按refundNo幂等执行原路全额退款，须在数据库事务之外调用。
     * @param request 原交易流水、金额币种及固定退款编号
     * @return 渠道执行结果，PROCESSING不能当作已退款
     * @throws com.qsx.appoint.common.BusinessException 原交易不符、同号异内容或渠道拒绝
     */
    GatewayRefundSubmission refund(GatewayRefundRequest request);

    /**
     * 查询渠道订单当前事实，不直接修改本地资金或预约状态。
     * @param providerOrderNo 渠道订单号
     * @return 渠道快照，未知订单由Adapter按其契约报错
     */
    GatewayPaymentSnapshot queryPayment(String providerOrderNo);

    /**
     * 验证原始通知签名并提取可信字段，业务完整性留给通知事务。
     * @param notification 尚未信任的原始报文
     * @return 已验签字段，不含原签名
     * @throws com.qsx.appoint.common.BusinessException 签名无效，错误码PAYMENT_SIGNATURE_INVALID
     */
    VerifiedPaymentNotification verifyNotification(RawPaymentNotification notification);
}

package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 退款单持久化；payment_order_id唯一，一张支付单最多一笔全额退款。
 * <p>所有锁/更新在payment父锁之后进行，SUCCEEDED不允许被迟到失败覆盖。
 */
@Mapper
public interface PaymentRefundMapper extends BaseMapper<PaymentRefund> {
    /**
     * 在payment父锁下查询并锁定其唯一退款，供申请幂等判断。
     * @return 原退款或null，不覆盖已存在的原因和编号
     */
    @Select("SELECT * FROM payment_refunds WHERE payment_order_id = #{paymentOrderId} FOR UPDATE")
    PaymentRefund selectByPaymentOrderIdForUpdate(@Param("paymentOrderId") Long paymentOrderId);

    /**
     * 先锁父支付，再按退款ID加锁；应用层复验所属payment。
     * @return 锁后退款或null
     */
    @Select("SELECT * FROM payment_refunds WHERE id = #{id} FOR UPDATE")
    PaymentRefund selectByIdForUpdate(@Param("id") Long id);

    /**
     * 仅PENDING/FAILED增加一次执行计数，保持状态，非分布式独占渠道执行标记。
     * @return 影响行数；渠道重复请求仍依赖refundNo幂等
     */
    @Update("UPDATE payment_refunds SET attempt_count = attempt_count + 1, updated_at = #{now} WHERE id = #{id} AND status IN ('PENDING', 'FAILED')")
    int beginAttempt(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    /**
     * 仅PENDING/FAILED→SUCCEEDED，记录渠道号和成功完成时间并清理失败摘要。
     * @return 影响行数，worker要求1，与资金REFUNDED及结果Outbox同事务
     */
    @Update("UPDATE payment_refunds SET status = 'SUCCEEDED', provider_refund_no = #{providerRefundNo}, completed_at = #{now}, updated_at = #{now}, last_failure_code = NULL, last_failure_message = NULL WHERE id = #{id} AND status IN ('PENDING', 'FAILED')")
    int markSucceeded(@Param("id") Long id, @Param("providerRefundNo") String providerRefundNo,
                      @Param("now") java.time.LocalDateTime now);

    /**
     * 仅PENDING/FAILED→FAILED，记录最近失败，不覆盖SUCCEEDED或填成功完成时间。
     * @return 影响行数；worker先锁后短路已成功退款
     */
    @Update("UPDATE payment_refunds SET status = 'FAILED', last_failure_code = #{code}, last_failure_message = #{message}, updated_at = #{now} WHERE id = #{id} AND status IN ('PENDING', 'FAILED')")
    int markFailed(@Param("id") Long id, @Param("code") String code, @Param("message") String message,
                   @Param("now") java.time.LocalDateTime now);
}


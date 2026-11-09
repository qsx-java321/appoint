package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 渠道尝试持久化；锁方法须在已持有payment父锁的事务内使用。
 * <p>CREATED由T2迁移，PROCESSING由渠道通知迁移，条件UPDATE保护已终结尝试。
 */
@Mapper
public interface PaymentAttemptMapper extends BaseMapper<PaymentAttempt> {
    /**
     * 按ID顺序锁支付单全部尝试，供父锁下检查是否已有CREATED/PROCESSING。
     * @return 尝试列表，无记录为空
     */
    @Select("SELECT * FROM payment_attempts WHERE payment_order_id = #{paymentOrderId} ORDER BY id FOR UPDATE")
    List<PaymentAttempt> selectByPaymentOrderIdForUpdate(@Param("paymentOrderId") Long paymentOrderId);

    /**
     * 在已持有payment父锁后锁定attempt，关联仍须应用层复验。
     * @return 尝试实体，无记录为null
     */
    @Select("SELECT * FROM payment_attempts WHERE id = #{id} FOR UPDATE")
    PaymentAttempt selectByIdForUpdate(@Param("id") Long id);

    /**
     * 按渠道+渠道订单号普通定位父支付，不先锁子记录。
     * @return 尝试或null，未知订单由通知用例决定是否拒绝审计
     */
    @Select("SELECT * FROM payment_attempts WHERE channel = #{channel} AND provider_order_no = #{providerOrderNo}")
    PaymentAttempt selectByProviderOrderNo(@Param("channel") String channel,
                                         @Param("providerOrderNo") String providerOrderNo);

    /**
     * 取得payment父锁后，按渠道订单号锁attempt并重新读取关联。
     * @return 锁后尝试，无记录为null
     */
    @Select("SELECT * FROM payment_attempts WHERE channel = #{channel} AND provider_order_no = #{providerOrderNo} FOR UPDATE")
    PaymentAttempt selectByProviderOrderNoForUpdate(@Param("channel") String channel,
                                                    @Param("providerOrderNo") String providerOrderNo);

    /**
     * 在payment父锁及refund检查后读取最近成功尝试，定位原路退款流水。
     * @return 最大ID的成功尝试，无成功尝试为null
     */
    @Select("SELECT * FROM payment_attempts WHERE payment_order_id = #{paymentOrderId} AND status = 'SUCCEEDED' ORDER BY id DESC LIMIT 1 FOR UPDATE")
    PaymentAttempt selectSucceededByPaymentOrderIdForUpdate(@Param("paymentOrderId") Long paymentOrderId);

    /**
     * T2只允许CREATED→PROCESSING并记录渠道订单号。
     * @return 影响行数，调用方要求1，不能覆盖回调已终结的尝试
     */
    @Update("""
            UPDATE payment_attempts
            SET provider_order_no = #{providerOrderNo}, status = 'PROCESSING',
                processing_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'CREATED'
            """)
    int markProcessing(@Param("id") Long id, @Param("providerOrderNo") String providerOrderNo,
                       @Param("now") java.time.LocalDateTime now);

    /**
     * T2只允许CREATED→FAILED，保存失败代码、摘要和完成时间。
     * @return 影响行数，调用方要求1
     */
    @Update("""
            UPDATE payment_attempts
            SET status = 'FAILED', failure_code = #{failureCode}, failure_message = #{failureMessage},
                completed_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'CREATED'
            """)
    int markFailed(@Param("id") Long id, @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("now") java.time.LocalDateTime now);

    /**
     * 成功通知只允许PROCESSING→SUCCEEDED并保存渠道流水。
     * @return 影响行数，与支付记账/Outbox同事务，调用方要求1
     */
    @Update("""
            UPDATE payment_attempts
            SET status = 'SUCCEEDED', provider_transaction_no = #{providerTransactionNo},
                completed_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markSucceeded(@Param("id") Long id, @Param("providerTransactionNo") String providerTransactionNo,
                      @Param("now") java.time.LocalDateTime now);

    /**
     * 失败通知只允许PROCESSING→FAILED，不能覆盖已经SUCCEEDED的尝试。
     * @return 影响行数，与支付恢复UNPAID同事务
     */
    @Update("""
            UPDATE payment_attempts
            SET status = 'FAILED', failure_code = 'CHANNEL_FAILED', failure_message = '渠道通知支付失败',
                completed_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markFailedByNotification(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
}


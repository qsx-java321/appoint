package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PaymentAttemptMapper extends BaseMapper<PaymentAttempt> {
    @Select("SELECT * FROM payment_attempts WHERE payment_order_id = #{paymentOrderId} ORDER BY id FOR UPDATE")
    List<PaymentAttempt> selectByPaymentOrderIdForUpdate(@Param("paymentOrderId") Long paymentOrderId);

    @Select("SELECT * FROM payment_attempts WHERE id = #{id} FOR UPDATE")
    PaymentAttempt selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM payment_attempts WHERE channel = #{channel} AND provider_order_no = #{providerOrderNo} FOR UPDATE")
    PaymentAttempt selectByProviderOrderNoForUpdate(@Param("channel") String channel,
                                                    @Param("providerOrderNo") String providerOrderNo);

    @Select("SELECT * FROM payment_attempts WHERE payment_order_id = #{paymentOrderId} AND status = 'SUCCEEDED' ORDER BY id DESC LIMIT 1 FOR UPDATE")
    PaymentAttempt selectSucceededByPaymentOrderIdForUpdate(@Param("paymentOrderId") Long paymentOrderId);

    @Update("""
            UPDATE payment_attempts
            SET provider_order_no = #{providerOrderNo}, status = 'PROCESSING',
                processing_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'CREATED'
            """)
    int markProcessing(@Param("id") Long id, @Param("providerOrderNo") String providerOrderNo,
                       @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE payment_attempts
            SET status = 'FAILED', failure_code = #{failureCode}, failure_message = #{failureMessage},
                completed_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'CREATED'
            """)
    int markFailed(@Param("id") Long id, @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE payment_attempts
            SET status = 'SUCCEEDED', provider_transaction_no = #{providerTransactionNo},
                completed_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markSucceeded(@Param("id") Long id, @Param("providerTransactionNo") String providerTransactionNo,
                      @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE payment_attempts
            SET status = 'FAILED', failure_code = 'CHANNEL_FAILED', failure_message = '渠道通知支付失败',
                completed_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markFailedByNotification(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);
}


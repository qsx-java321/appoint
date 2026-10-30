package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentRefundMapper extends BaseMapper<PaymentRefund> {
    @Select("SELECT * FROM payment_refunds WHERE payment_order_id = #{paymentOrderId} FOR UPDATE")
    PaymentRefund selectByPaymentOrderIdForUpdate(@Param("paymentOrderId") Long paymentOrderId);

    @Select("SELECT * FROM payment_refunds WHERE id = #{id} FOR UPDATE")
    PaymentRefund selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE payment_refunds SET attempt_count = attempt_count + 1, updated_at = #{now} WHERE id = #{id} AND status IN ('PENDING', 'FAILED')")
    int beginAttempt(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    @Update("UPDATE payment_refunds SET status = 'SUCCEEDED', provider_refund_no = #{providerRefundNo}, completed_at = #{now}, updated_at = #{now}, last_failure_code = NULL, last_failure_message = NULL WHERE id = #{id} AND status IN ('PENDING', 'FAILED')")
    int markSucceeded(@Param("id") Long id, @Param("providerRefundNo") String providerRefundNo,
                      @Param("now") java.time.LocalDateTime now);

    @Update("UPDATE payment_refunds SET status = 'FAILED', last_failure_code = #{code}, last_failure_message = #{message}, updated_at = #{now} WHERE id = #{id} AND status IN ('PENDING', 'FAILED')")
    int markFailed(@Param("id") Long id, @Param("code") String code, @Param("message") String message,
                   @Param("now") java.time.LocalDateTime now);
}


package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
    @Update("UPDATE payment_orders SET status = 'PAID', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'UNPAID'")
    int payIfUnpaid(@Param("id") Long id);

    @Select("SELECT * FROM payment_orders WHERE id = #{id}")
    PaymentOrder selectByIdForLookup(@Param("id") Long id);

    @Select("SELECT * FROM payment_orders WHERE id = #{id} FOR UPDATE")
    PaymentOrder selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM payment_orders WHERE appointment_order_id = #{appointmentOrderId} FOR UPDATE")
    PaymentOrder selectByAppointmentOrderIdForUpdate(@Param("appointmentOrderId") Long appointmentOrderId);

    @Update("UPDATE payment_orders SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status IN ('UNPAID', 'PAYING')")
    int closeIfUnpaid(@Param("id") Long id);

    @Update("UPDATE payment_orders SET status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PAID'")
    int refundIfPaid(@Param("id") Long id);

    @Update("UPDATE payment_orders SET status = 'REFUND_PENDING', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PAID'")
    int requestRefundIfPaid(@Param("id") Long id);

    @Update("UPDATE payment_orders SET status = 'REFUNDED', refunded_at = #{refundedAt}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'REFUND_PENDING'")
    int completeRefund(@Param("id") Long id, @Param("refundedAt") java.time.LocalDateTime refundedAt);

    @Update("UPDATE payment_orders SET status = 'PAYING', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'UNPAID'")
    int startPayingIfUnpaid(@Param("id") Long id);

    @Update("UPDATE payment_orders SET status = 'UNPAID', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PAYING'")
    int restoreUnpaidIfPaying(@Param("id") Long id);

    @Update("UPDATE payment_orders SET status = 'PAID', paid_at = #{paidAt}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PAYING'")
    int payIfPaying(@Param("id") Long id, @Param("paidAt") java.time.LocalDateTime paidAt);

    @Update("UPDATE payment_orders SET status = 'PAID', paid_at = #{paidAt}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'CLOSED'")
    int recordLatePaymentIfClosed(@Param("id") Long id, @Param("paidAt") java.time.LocalDateTime paidAt);
}

package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
    @Select("SELECT * FROM payment_orders WHERE appointment_order_id = #{appointmentOrderId} FOR UPDATE")
    PaymentOrder selectByAppointmentOrderIdForUpdate(@Param("appointmentOrderId") Long appointmentOrderId);

    @Update("""
            UPDATE payment_orders SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'UNPAID'
            """)
    int closeIfUnpaid(@Param("id") Long id);

    @Update("""
            UPDATE payment_orders SET status = 'PAID', paid_at = #{paidAt}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'UNPAID'
            """)
    int payIfUnpaid(@Param("id") Long id, @Param("paidAt") LocalDateTime paidAt);
}

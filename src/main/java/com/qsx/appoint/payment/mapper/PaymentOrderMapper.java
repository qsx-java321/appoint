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

    @Select("SELECT * FROM payment_orders WHERE appointment_order_id = #{appointmentOrderId} FOR UPDATE")
    PaymentOrder selectByAppointmentOrderIdForUpdate(@Param("appointmentOrderId") Long appointmentOrderId);

    @Update("UPDATE payment_orders SET status = 'CLOSED', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'UNPAID'")
    int closeIfUnpaid(@Param("id") Long id);
}

package com.qsx.appoint.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AppointmentOrderMapper extends BaseMapper<AppointmentOrder> {
    @Select("""
            SELECT a.id,
                   a.appointment_no,
                   a.user_id AS appointment_user_id,
                   a.schedule_id,
                   a.status,
                   a.created_at,
                   p.id AS payment_order_id,
                   p.user_id AS payment_user_id,
                   p.status AS payment_status
            FROM appointment_orders a
            LEFT JOIN payment_orders p ON p.appointment_order_id = a.id
            WHERE a.id = #{id}
            """)
    AppointmentQueryRecord selectQueryById(@Param("id") Long id);

    @Select("""
            SELECT a.id,
                   a.appointment_no,
                   a.user_id AS appointment_user_id,
                   a.schedule_id,
                   a.status,
                   a.created_at,
                   p.id AS payment_order_id,
                   p.user_id AS payment_user_id,
                   p.status AS payment_status
            FROM appointment_orders a
            LEFT JOIN payment_orders p ON p.appointment_order_id = a.id
            WHERE a.user_id = #{userId}
            ORDER BY a.id DESC
            """)
    List<AppointmentQueryRecord> selectQueriesByUserId(@Param("userId") Long userId);

    @Update("UPDATE appointment_orders SET status = 'CONFIRMED', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PENDING_PAYMENT'")
    int confirmIfPending(@Param("id") Long id);
}

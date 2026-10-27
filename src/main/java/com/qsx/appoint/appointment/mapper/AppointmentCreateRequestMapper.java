package com.qsx.appoint.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.appointment.entity.AppointmentCreateRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentCreateRequestMapper extends BaseMapper<AppointmentCreateRequest> {
    @Select("SELECT * FROM appointment_create_requests WHERE user_id = #{userId} AND idempotency_key = #{key}")
    AppointmentCreateRequest selectByUserAndKey(@Param("userId") Long userId, @Param("key") String key);

    @Update("""
            UPDATE appointment_create_requests
            SET appointment_order_id = #{appointmentOrderId}, response_json = #{responseJson}
            WHERE id = #{id} AND appointment_order_id IS NULL
            """)
    int complete(@Param("id") Long id, @Param("appointmentOrderId") Long appointmentOrderId,
                 @Param("responseJson") String responseJson);
}

package com.qsx.appoint.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentOrderMapper extends BaseMapper<AppointmentOrder> {
    @Update("UPDATE appointment_orders SET status = 'CONFIRMED', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PENDING_PAYMENT'")
    int confirmIfPending(@Param("id") Long id);
}

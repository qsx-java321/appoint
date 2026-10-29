package com.qsx.appoint.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.appointment.entity.AppointmentActiveClaim;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppointmentActiveClaimMapper extends BaseMapper<AppointmentActiveClaim> {
    @Delete("DELETE FROM appointment_active_claims WHERE appointment_order_id = #{appointmentOrderId}")
    int deleteByAppointmentOrderId(@Param("appointmentOrderId") Long appointmentOrderId);
}

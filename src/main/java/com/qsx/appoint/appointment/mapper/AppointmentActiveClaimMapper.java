package com.qsx.appoint.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.appointment.entity.AppointmentActiveClaim;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 有效预约占用持久化；唯一键约束同场次就诊人和跨账号证件，关闭时删除。 */
@Mapper
public interface AppointmentActiveClaimMapper extends BaseMapper<AppointmentActiveClaim> {
    /**
     * 按预约删除唯一有效占用，与预约关闭/库存释放同事务。
     * @param appointmentOrderId 正在关闭的预约单ID
     * @return 删除行数，完整生命周期要求1；0不能静默当作释放成功
     */
    @Delete("DELETE FROM appointment_active_claims WHERE appointment_order_id = #{appointmentOrderId}")
    int deleteByAppointmentOrderId(@Param("appointmentOrderId") Long appointmentOrderId);
}

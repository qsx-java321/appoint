package com.qsx.appoint.visit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.visit.entity.VisitRecord;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface VisitRecordMapper extends BaseMapper<VisitRecord> {
    @Select("SELECT * FROM visit_records WHERE appointment_order_id = #{appointmentId}")
    VisitRecord selectByAppointmentOrderId(@Param("appointmentId") Long appointmentId);

    @Update("""
            UPDATE visit_records SET status = 'IN_PROGRESS', started_at = #{now}, updated_at = #{now}
            WHERE appointment_order_id = #{appointmentId} AND status = 'WAITING'
            """)
    int startIfWaiting(@Param("appointmentId") Long appointmentId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE visit_records SET status = 'COMPLETED', completed_at = #{now}, updated_at = #{now}
            WHERE appointment_order_id = #{appointmentId} AND status = 'IN_PROGRESS'
            """)
    int completeIfInProgress(@Param("appointmentId") Long appointmentId, @Param("now") LocalDateTime now);
}

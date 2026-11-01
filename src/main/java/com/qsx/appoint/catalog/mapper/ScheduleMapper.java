package com.qsx.appoint.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {
    @Select("SELECT COUNT(*) FROM schedules WHERE doctor_id = #{doctorId}")
    long countByDoctorId(@Param("doctorId") Long doctorId);

    @Select("SELECT * FROM schedules WHERE id = #{scheduleId} FOR UPDATE")
    Schedule selectByIdForUpdate(@Param("scheduleId") Long scheduleId);

    @Update("""
            UPDATE schedules
            SET available_capacity = available_capacity - 1,
                held_capacity = held_capacity + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{scheduleId} AND session_type IS NOT NULL
              AND status = 'OPEN' AND available_capacity > 0
            """)
    int holdOne(@Param("scheduleId") Long scheduleId);

    @Update("""
            UPDATE schedules
            SET held_capacity = held_capacity - 1,
                confirmed_capacity = confirmed_capacity + 1,
                next_visit_number = next_visit_number + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{scheduleId} AND status = 'OPEN'
              AND held_capacity > 0 AND next_visit_number = #{visitNumber}
            """)
    int confirmOne(@Param("scheduleId") Long scheduleId, @Param("visitNumber") Integer visitNumber);

    @Update("""
            UPDATE schedules
            SET held_capacity = held_capacity - 1,
                available_capacity = available_capacity + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{scheduleId} AND held_capacity > 0
            """)
    int releaseHeldOne(@Param("scheduleId") Long scheduleId);

    @Update("""
            UPDATE schedules
            SET confirmed_capacity = confirmed_capacity - 1,
                available_capacity = available_capacity + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{scheduleId} AND confirmed_capacity > 0
            """)
    int releaseConfirmedOne(@Param("scheduleId") Long scheduleId);

    @Update("""
            UPDATE schedules
            SET total_capacity = total_capacity + #{delta},
                available_capacity = available_capacity + #{delta},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{scheduleId} AND status = 'OPEN'
              AND total_capacity + #{delta} >= 0
              AND available_capacity + #{delta} >= 0
            """)
    int adjustAvailable(@Param("scheduleId") Long scheduleId, @Param("delta") int delta);

    @Update("""
            UPDATE schedules
            SET status = 'CLOSED', closed_at = #{closedAt}, closed_by = #{closedBy},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{scheduleId} AND status = 'OPEN' AND session_type IS NOT NULL
            """)
    int closeIfOpen(@Param("scheduleId") Long scheduleId,
                    @Param("closedAt") java.time.LocalDateTime closedAt,
                    @Param("closedBy") Long closedBy);
}

package com.qsx.appoint.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {
    @Select("SELECT COUNT(*) FROM schedules WHERE doctor_id = #{doctorId}")
    long countByDoctorId(@Param("doctorId") Long doctorId);

    @Select("SELECT * FROM schedules WHERE id = #{scheduleId} FOR UPDATE")
    Schedule selectByIdForUpdate(@Param("scheduleId") Long scheduleId);

    @Select("""
            SELECT * FROM schedules
            WHERE doctor_id = #{doctorId} AND appointment_date = #{appointmentDate}
              AND session_type = #{sessionType}
            """)
    Schedule selectByDoctorDateSession(@Param("doctorId") Long doctorId,
                                       @Param("appointmentDate") java.time.LocalDate appointmentDate,
                                       @Param("sessionType") String sessionType);

    @Select("""
            SELECT * FROM schedules
            WHERE doctor_id = #{doctorId} AND appointment_date = #{appointmentDate}
              AND session_type = #{sessionType}
            FOR UPDATE
            """)
    Schedule selectByDoctorDateSessionForUpdate(@Param("doctorId") Long doctorId,
                                                @Param("appointmentDate") java.time.LocalDate appointmentDate,
                                                @Param("sessionType") String sessionType);

    @Insert("""
            INSERT IGNORE INTO schedules (
                doctor_id, appointment_date, start_time, end_time, status, session_type,
                total_capacity, available_capacity, held_capacity, confirmed_capacity,
                next_visit_number, registration_fee, fee_currency)
            VALUES (
                #{doctorId}, #{appointmentDate}, #{startTime}, #{endTime}, 'OPEN', #{sessionType},
                #{capacity}, #{capacity}, 0, 0, 1, #{registrationFee}, #{currency})
            """)
    int insertGenerated(@Param("doctorId") Long doctorId,
                        @Param("appointmentDate") java.time.LocalDate appointmentDate,
                        @Param("startTime") java.time.LocalTime startTime,
                        @Param("endTime") java.time.LocalTime endTime,
                        @Param("sessionType") String sessionType,
                        @Param("capacity") int capacity,
                        @Param("registrationFee") java.math.BigDecimal registrationFee,
                        @Param("currency") String currency);

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
            SET available_capacity = available_capacity - 1,
                held_capacity = held_capacity + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{scheduleId} AND session_type IN (
                    'MORNING_FIRST', 'MORNING_SECOND', 'AFTERNOON_FIRST', 'AFTERNOON_SECOND')
              AND status = 'OPEN' AND available_capacity > 0
              AND appointment_date > #{today}
            """)
    int holdOneFutureSession(@Param("scheduleId") Long scheduleId, @Param("today") LocalDate today);

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

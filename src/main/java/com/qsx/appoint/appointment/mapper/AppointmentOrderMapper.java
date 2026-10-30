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
    @Update("UPDATE appointment_orders SET status = 'CONFIRMED', visit_number = #{visitNumber}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PENDING_PAYMENT'")
    int confirmIfPending(@Param("id") Long id, @Param("visitNumber") Integer visitNumber);

    @Select("SELECT * FROM appointment_orders WHERE id = #{id}")
    AppointmentOrder selectByIdForLookup(@Param("id") Long id);

    @Select("SELECT * FROM appointment_orders WHERE id = #{id} FOR UPDATE")
    AppointmentOrder selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE appointment_orders
            SET status = #{status}, closed_at = #{closedAt}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int closeIfPending(@Param("id") Long id, @Param("status") String status,
                       @Param("closedAt") java.time.LocalDateTime closedAt);

    @Update("""
            UPDATE appointment_orders
            SET status = 'CANCELLED', closed_at = #{closedAt}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'CONFIRMED'
            """)
    int cancelIfConfirmed(@Param("id") Long id,
                          @Param("closedAt") java.time.LocalDateTime closedAt);

    @Select("SELECT * FROM appointment_orders WHERE schedule_id = #{scheduleId} AND status = 'PENDING_PAYMENT' ORDER BY id FOR UPDATE")
    List<AppointmentOrder> selectPendingByScheduleForUpdate(@Param("scheduleId") Long scheduleId);

    @Select("""
            <script>
            SELECT id, payment_expires_at
            FROM appointment_orders
            WHERE status = 'PENDING_PAYMENT'
              AND payment_expires_at &lt;= #{scanTime}
            <if test="cursorExpiresAt != null">
              AND (payment_expires_at &gt; #{cursorExpiresAt}
                   OR (payment_expires_at = #{cursorExpiresAt} AND id &gt; #{cursorId}))
            </if>
            ORDER BY payment_expires_at, id
            LIMIT #{limit}
            </script>
            """)
    List<ExpirationCandidate> selectExpirationCandidates(
            @Param("scanTime") java.time.LocalDateTime scanTime,
            @Param("cursorExpiresAt") java.time.LocalDateTime cursorExpiresAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}

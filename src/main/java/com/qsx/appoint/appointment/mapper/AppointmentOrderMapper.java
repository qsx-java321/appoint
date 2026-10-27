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
                   a.patient_id,
                   a.patient_name,
                   a.patient_document_type,
                   a.patient_document_no,
                   a.patient_contact_phone,
                   a.patient_contact_email,
                   a.doctor_id,
                   a.doctor_no,
                   a.doctor_name,
                   a.doctor_title,
                   a.department_id,
                   a.department_name,
                   a.hospital_id,
                   a.hospital_name,
                   a.hospital_address,
                   a.appointment_date,
                   a.start_time,
                   a.end_time,
                   a.status,
                   a.created_at,
                   a.payment_expires_at,
                   a.closed_at,
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
                   a.patient_id,
                   a.patient_name,
                   a.patient_document_type,
                   a.patient_document_no,
                   a.patient_contact_phone,
                   a.patient_contact_email,
                   a.doctor_id,
                   a.doctor_no,
                   a.doctor_name,
                   a.doctor_title,
                   a.department_id,
                   a.department_name,
                   a.hospital_id,
                   a.hospital_name,
                   a.hospital_address,
                   a.appointment_date,
                   a.start_time,
                   a.end_time,
                   a.status,
                   a.created_at,
                   a.payment_expires_at,
                   a.closed_at,
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

    @Select("SELECT * FROM appointment_orders WHERE id = #{id} FOR UPDATE")
    AppointmentOrder selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE appointment_orders
            SET status = #{status}, closed_at = #{closedAt}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int closeIfPending(@Param("id") Long id, @Param("status") String status,
                       @Param("closedAt") java.time.LocalDateTime closedAt);

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

package com.qsx.appoint.bookingquery.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 跨写模块只读 SQL 的唯一归属；本 Mapper 不声明任何写操作。 */
@Mapper
public interface BookingQueryMapper {
    String PROJECTION = """
            SELECT a.id, a.appointment_no, a.user_id AS appointment_user_id, a.schedule_id,
                   a.patient_id, a.patient_name, a.patient_document_type, a.patient_document_no,
                   a.patient_contact_phone, a.patient_contact_email, a.doctor_id, a.doctor_no,
                   a.doctor_name, a.doctor_title, a.department_id, a.department_name,
                   a.hospital_id, a.hospital_name, a.hospital_address, a.appointment_date,
                   a.start_time, a.end_time, a.status, a.visit_number, a.created_at,
                   a.payment_expires_at, a.closed_at, s.status AS schedule_status,
                   p.id AS payment_order_id, p.user_id AS payment_user_id, p.status AS payment_status,
                   p.amount, p.currency,
                   (SELECT COUNT(*) FROM payment_attempts pac WHERE pac.payment_order_id = p.id)
                       AS payment_attempt_count,
                   pa.id AS attempt_id, pa.attempt_no, pa.channel AS attempt_channel,
                   pa.status AS attempt_status, pa.provider_order_no,
                   pa.failure_code AS attempt_failure_code, pa.created_at AS attempt_created_at,
                   pa.completed_at AS attempt_completed_at,
                   r.id AS refund_id, r.refund_no, r.amount AS refund_amount,
                   r.currency AS refund_currency, r.channel AS refund_channel,
                   r.status AS refund_status, r.reason AS refund_reason,
                   r.attempt_count AS refund_attempt_count,
                   r.last_failure_code AS refund_last_failure_code,
                   r.completed_at AS refund_completed_at
            FROM appointment_orders a
            LEFT JOIN payment_orders p ON p.appointment_order_id = a.id
            LEFT JOIN schedules s ON s.id = a.schedule_id
            LEFT JOIN payment_attempts pa ON pa.id = (
                SELECT MAX(pa2.id) FROM payment_attempts pa2 WHERE pa2.payment_order_id = p.id)
            LEFT JOIN payment_refunds r ON r.payment_order_id = p.id
            """;

    @Select(PROJECTION + " WHERE a.id = #{id}")
    BookingQueryRecord selectById(@Param("id") Long id);

    @Select(PROJECTION + " WHERE a.user_id = #{userId} ORDER BY a.id DESC")
    List<BookingQueryRecord> selectByUserId(@Param("userId") Long userId);
}

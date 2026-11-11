package com.qsx.appoint.bookingquery.persistence;

import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

/** 仅执行静态只读联查，不继承 BaseMapper，也不领取行锁。 */
@Mapper
public interface DoctorVisitQueryMapper {
    String PROJECTION = """
            SELECT a.id AS appointment_id, a.appointment_no, a.visit_number,
                   a.appointment_date, s.session_type, a.start_time, a.end_time,
                   a.patient_id, a.patient_name, a.patient_document_type, a.patient_document_no,
                   a.doctor_id, a.doctor_no, a.doctor_name, a.doctor_title,
                   a.department_id, a.department_name, a.hospital_id, a.hospital_name, a.hospital_address,
                   v.status AS visit_status, v.checked_in_at, v.started_at, v.completed_at
            FROM appointment_orders a
            JOIN schedules s ON s.id = a.schedule_id
            LEFT JOIN visit_records v ON v.appointment_order_id = a.id
            """;

    @Select(PROJECTION + """
             WHERE a.doctor_id = #{doctorId} AND a.appointment_date = #{date}
               AND s.session_type = #{sessionType} AND a.status = 'CONFIRMED'
             ORDER BY a.visit_number ASC
            """)
    List<DoctorVisitQueryRecord> selectByDoctorDateSession(@Param("doctorId") Long doctorId,
            @Param("date") LocalDate date, @Param("sessionType") String sessionType);

    @Select(PROJECTION + " WHERE a.doctor_id = #{doctorId} AND a.id = #{appointmentId} AND a.status = 'CONFIRMED'")
    DoctorVisitQueryRecord selectOwnedDetail(@Param("doctorId") Long doctorId, @Param("appointmentId") Long appointmentId);

    @Select("""
            SELECT s.session_type, COUNT(*) AS confirmed_count,
                   SUM(CASE WHEN v.id IS NULL THEN 1 ELSE 0 END) AS not_checked_in_count,
                   SUM(CASE WHEN v.status = 'WAITING' THEN 1 ELSE 0 END) AS waiting_count,
                   SUM(CASE WHEN v.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_count,
                   SUM(CASE WHEN v.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count
            FROM appointment_orders a
            JOIN schedules s ON s.id = a.schedule_id
            LEFT JOIN visit_records v ON v.appointment_order_id = a.id
            WHERE a.doctor_id = #{doctorId} AND a.appointment_date = #{date} AND a.status = 'CONFIRMED'
              AND s.session_type IN ('MORNING_FIRST','MORNING_SECOND','AFTERNOON_FIRST','AFTERNOON_SECOND')
            GROUP BY s.session_type
            """)
    List<DoctorSessionCountRecord> countByDoctorAndDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
}

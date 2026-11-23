package com.qsx.appoint.bookingquery.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 预约详情/列表跨表联查的只读Mapper，不继承写Mapper或获取行锁。
 * <p>预约侧姓名/地址为创建快照，支付结果按支付单唯一关联。
 * LEFT JOIN保留缺失支付的异常预约，让上层先做归属判断再报告完整性。
 */
@Mapper
public interface BookingQueryMapper {
    String PROJECTION = """
            SELECT a.id, a.appointment_no, a.user_id AS appointment_user_id, a.schedule_id,
                   a.patient_id, a.patient_name, a.patient_document_type, a.patient_document_no,
                   a.patient_contact_phone, a.patient_contact_email, a.doctor_id, a.doctor_no,
                   a.doctor_name, a.doctor_title, a.department_id, a.department_name,
                   a.hospital_id, a.hospital_name, a.hospital_address, a.appointment_date,
                   a.start_time, a.end_time, a.status, a.visit_number, a.created_at,
                   a.payment_expires_at, a.closed_at, s.session_type,
                   v.id AS visit_record_id, v.status AS visit_status,
                   v.checked_in_at, v.started_at, v.completed_at,
                   p.id AS payment_order_id, p.user_id AS payment_user_id, p.status AS payment_status,
                   p.amount, p.currency, p.paid_at
            FROM appointment_orders a
            LEFT JOIN payment_orders p ON p.appointment_order_id = a.id
            LEFT JOIN schedules s ON s.id = a.schedule_id
            LEFT JOIN visit_records v ON v.appointment_order_id = a.id
            """;

    /**
     * 按预约ID只读联查，不在SQL中代替用例执行账号归属检查。
     * @param id 预约主键
     * @return 组合行；预约不存在返回null，支付缺失时支付字段仍可能为空
     */
    @Select(PROJECTION + " WHERE a.id = #{id}")
    BookingQueryRecord selectById(@Param("id") Long id);

    /**
     * 按账号读取预约及支付摘要，就诊日期、开始时间、ID降序；不加锁、不产生任何写入。
     * @param userId 账号用户主键
     * @return 组合行列表，无预约时为空
     */
    @Select(PROJECTION + " WHERE a.user_id = #{userId} ORDER BY a.appointment_date DESC, a.start_time DESC, a.id DESC")
    List<BookingQueryRecord> selectByUserId(@Param("userId") Long userId);
    @Select(PROJECTION + " WHERE a.user_id = #{userId} ORDER BY a.appointment_date ASC, a.start_time ASC, a.id ASC")
    List<BookingQueryRecord> selectByUserIdAsc(@Param("userId") Long userId);
}

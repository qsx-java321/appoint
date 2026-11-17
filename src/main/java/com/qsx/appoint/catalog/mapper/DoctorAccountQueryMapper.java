package com.qsx.appoint.catalog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DoctorAccountQueryMapper {
    @Select("""
            SELECT h.id AS hospital_id, h.name AS hospital_name,
                   dep.id AS department_id, dep.name AS department_name,
                   d.id AS doctor_id, d.doctor_no, d.name AS doctor_name,
                   u.id AS user_id, u.email,
                   CASE WHEN s.user_id IS NOT NULL THEN TRUE ELSE FALSE END AS seeded_account
            FROM doctors d
            JOIN departments dep ON dep.id = d.department_id
            JOIN hospitals h ON h.id = dep.hospital_id
            LEFT JOIN users u ON u.id = d.user_id
            LEFT JOIN local_environment_seed_accounts s
              ON s.user_id = u.id AND s.email = u.email AND s.role_code = 'DOCTOR'
            ORDER BY h.id, dep.id, d.doctor_no, d.id
            """)
    List<Row> listAccounts();

    record Row(Long hospitalId, String hospitalName, Long departmentId, String departmentName,
               Long doctorId, String doctorNo, String doctorName, Long userId, String email,
               boolean seededAccount) {}
}

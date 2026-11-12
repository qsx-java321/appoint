package com.qsx.appoint.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static com.qsx.appoint.support.MySqlConstraintAssertions.assertCheckViolation;

/** 类独占MySQL执行V9到V10升级，验证历史订单保留、医生绑定及就诊约束和索引。 */
@Testcontainers
class VisitSchemaMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appoint").withUsername("appoint").withPassword("appoint-test");

    @Test
    void incrementalUpgradePreservesHistoricalAppointmentAndEnforcesVisitAndDoctorConstraints() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).target("9").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(9001,'visit-migration@example.com','hash')");
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email) VALUES(9002,9001,'历史就诊人','RESIDENT_ID_CARD','110105199001010010','13800138000','p@example.com')");
        jdbc.update("""
                INSERT INTO appointment_orders(id,appointment_no,user_id,schedule_id,patient_id,patient_name,
                    patient_document_type,patient_document_no,patient_contact_phone,patient_contact_email,
                    doctor_id,doctor_no,doctor_name,doctor_title,department_id,department_name,
                    hospital_id,hospital_name,hospital_address,appointment_date,start_time,end_time,status,payment_expires_at)
                SELECT 9003,'APVISITHISTORY',9001,s.id,9002,'历史就诊人','RESIDENT_ID_CARD','110105199001010010',
                    '13800138000','p@example.com',d.id,d.doctor_no,d.name,d.title,dep.id,dep.name,h.id,h.name,h.address,
                    s.appointment_date,s.start_time,s.end_time,'CONFIRMED','2098-12-31 08:15:00'
                FROM schedules s JOIN doctors d ON d.id=s.doctor_id JOIN departments dep ON dep.id=d.department_id
                    JOIN hospitals h ON h.id=dep.hospital_id WHERE s.id=1
                """);
        var previous = jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=9003");
        var flyway = Flyway.configure().dataSource(source).target("10").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=9003")).isEqualTo(previous);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctors WHERE user_id IS NOT NULL", Integer.class)).isZero();
        jdbc.update("UPDATE doctors SET user_id=9001 WHERE id=1");
        assertThatThrownBy(() -> jdbc.update("UPDATE doctors SET user_id=9001 WHERE id=2")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE doctors SET user_id=999999 WHERE id=2")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO visit_records(appointment_order_id,status,checked_in_at) VALUES(9003,'WAITING','2026-11-13 08:00:00')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO visit_records(appointment_order_id,status,checked_in_at) VALUES(9003,'WAITING',NOW())")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO visit_records(appointment_order_id,status,checked_in_at) VALUES(999999,'WAITING',NOW())")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertCheckViolation(() -> jdbc.update("UPDATE visit_records SET status='NOT_CHECKED_IN'"), "ck_visit_records_status");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM appointment_orders WHERE id=9003")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name IN ('idx_appointments_user_visit_time','idx_appointments_doctor_visit_date')", Integer.class)).isEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT user_id FROM doctors WHERE id=2", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctors WHERE user_id IS NOT NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM visit_records WHERE appointment_order_id=9003", String.class)).isEqualTo("WAITING");
        assertThat(jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=9003")).isEqualTo(previous);
    }
}

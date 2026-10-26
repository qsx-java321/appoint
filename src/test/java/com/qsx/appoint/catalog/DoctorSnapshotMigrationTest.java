package com.qsx.appoint.catalog;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorSnapshotMigrationTest {

    @Test
    void migrationBackfillsDoctorNumbersAndLegacyAppointmentSnapshot() {
        String databaseName = "legacy-appointment-" + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO users (email, password_hash) VALUES (?, ?)", "legacy@example.com", "hash");
        jdbc.update("INSERT INTO appointment_orders (appointment_no, user_id, schedule_id, status) VALUES (?, ?, ?, ?)",
                "AP-LEGACY", 1L, 1L, "PENDING_PAYMENT");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT doctor_no FROM doctors WHERE id = 1", String.class))
                .isEqualTo("DOC000001");
        Map<String, Object> snapshot = jdbc.queryForMap("""
                SELECT doctor_id, doctor_no, doctor_name, doctor_title,
                       department_id, department_name, hospital_id, hospital_name,
                       hospital_address, appointment_date, start_time, end_time,
                       payment_expires_at, closed_at
                FROM appointment_orders
                WHERE appointment_no = 'AP-LEGACY'
                """);
        assertThat(snapshot)
                .containsEntry("doctor_id", 1L)
                .containsEntry("doctor_no", "DOC000001")
                .containsEntry("doctor_name", "张医生")
                .containsEntry("doctor_title", "主任医师")
                .containsEntry("department_id", 1L)
                .containsEntry("department_name", "内科")
                .containsEntry("hospital_id", 1L)
                .containsEntry("hospital_name", "示例市第一人民医院")
                .containsEntry("hospital_address", "示例市健康路 1 号");
        assertThat(snapshot.get("appointment_date").toString()).isEqualTo("2099-01-01");
        assertThat(snapshot.get("start_time").toString()).startsWith("09:00");
        assertThat(snapshot.get("end_time").toString()).startsWith("09:30");
        assertThat(snapshot.get("payment_expires_at")).isNotNull();
        assertThat(snapshot.get("closed_at")).isNull();
    }
}

package com.qsx.appoint.catalog;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitialSchemaMigrationTest {
    @Test
    void emptyDatabaseIsInitializedOnceWithFinalSchemaAndSeedCatalog() {
        String databaseName = "initial-schema-" + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(7);
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version = '1'",
                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hospitals", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctors", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT doctor_no FROM doctors WHERE id = 1", String.class))
                .isEqualTo("DOC000001");
        assertThat(jdbc.queryForObject("SELECT MAX(id) FROM doctor_number_allocations", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedules WHERE session_type IS NOT NULL", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedules WHERE session_type IS NULL", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'schedules' AND column_name = 'registration_fee'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'payment_orders' AND column_name = 'legacy'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_name IN ('payment_attempts', 'payment_refunds', 'payment_notifications',
                    'payment_outbox_events', 'coordination_inbox_events')
                """, String.class)).containsExactlyInAnyOrder(
                "payment_attempts", "payment_refunds", "payment_notifications",
                "payment_outbox_events", "coordination_inbox_events");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_create_requests", Integer.class)).isZero();
    }

    @Test
    void activeLegacyAppointmentStopsIncrementalMigrationBeforeSchemaChanges() {
        String databaseName = "legacy-preflight-" + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO users (email, password_hash) VALUES ('legacy@example.com', 'unused')");
        jdbc.update("""
                INSERT INTO patients (user_id, name, document_type, document_no, contact_phone, contact_email)
                VALUES (1, '旧就诊人', 'RESIDENT_ID_CARD', '110105199001010010', '13800138000', 'legacy@example.com')
                """);
        jdbc.update("""
                INSERT INTO appointment_orders (
                    appointment_no, user_id, schedule_id, patient_id, patient_name,
                    patient_document_type, patient_document_no, patient_contact_phone, patient_contact_email,
                    doctor_id, doctor_no, doctor_name, doctor_title, department_id, department_name,
                    hospital_id, hospital_name, hospital_address, appointment_date, start_time, end_time,
                    status, payment_expires_at)
                VALUES ('APLEGACY', 1, 1, 1, '旧就诊人', 'RESIDENT_ID_CARD', '110105199001010010',
                    '13800138000', 'legacy@example.com', 1, 'DOC000001', '张医生', '主任医师', 1, '内科',
                    1, '示例市第一人民医院', '示例市健康路 1 号', '2099-01-01', '09:00:00', '09:30:00',
                    'PENDING_PAYMENT', '2098-12-31 09:00:00')
                """);

        Flyway full = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        assertThatThrownBy(full::migrate)
                .hasStackTraceContaining("旧单号源有效订单");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'schedules' AND column_name = 'session_type'",
                Integer.class)).isZero();
    }

    @Test
    void activePreRefactorPaymentStopsV4BeforePaymentSchemaChanges() {
        DriverManagerDataSource dataSource = v3DataSource("payment-v4-preflight-");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertV3Order(jdbc, "PENDING_PAYMENT", "UNPAID");

        Flyway full = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        assertThatThrownBy(full::migrate)
                .hasStackTraceContaining("旧活跃支付");

        assertThat(jdbc.queryForObject("SELECT status FROM appointment_orders WHERE appointment_no = 'APV4'", String.class))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE payment_no = 'PAYV4'", String.class))
                .isEqualTo("UNPAID");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'payment_attempts'",
                Integer.class)).isZero();
    }

    @Test
    void terminalPreRefactorPaymentPassesV4WithoutBeingRewritten() {
        DriverManagerDataSource dataSource = v3DataSource("payment-v4-terminal-");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertV3Order(jdbc, "CANCELLED", "CLOSED");

        Flyway full = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        assertThat(full.migrate().migrationsExecuted).isEqualTo(4);

        assertThat(jdbc.queryForObject("SELECT status FROM appointment_orders WHERE appointment_no = 'APV4'", String.class))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE payment_no = 'PAYV4'", String.class))
                .isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT legacy FROM payment_orders WHERE payment_no = 'PAYV4'", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_orders WHERE payment_no = 'PAYV4'", java.math.BigDecimal.class))
                .isNull();
    }

    private DriverManagerDataSource v3DataSource(String prefix) {
        String databaseName = prefix + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3")).load().migrate();
        return dataSource;
    }

    private void insertV3Order(JdbcTemplate jdbc, String appointmentStatus, String paymentStatus) {
        jdbc.update("INSERT INTO users (email, password_hash) VALUES ('payment-v4@example.com', 'unused')");
        jdbc.update("""
                INSERT INTO patients (user_id, name, document_type, document_no, contact_phone, contact_email)
                VALUES (1, 'V4 就诊人', 'RESIDENT_ID_CARD', '110105199001010010', '13800138000',
                    'payment-v4@example.com')
                """);
        jdbc.update("""
                INSERT INTO appointment_orders (
                    appointment_no, user_id, schedule_id, patient_id, patient_name,
                    patient_document_type, patient_document_no, patient_contact_phone, patient_contact_email,
                    doctor_id, doctor_no, doctor_name, doctor_title, department_id, department_name,
                    hospital_id, hospital_name, hospital_address, appointment_date, start_time, end_time,
                    status, payment_expires_at)
                VALUES ('APV4', 1, 4, 1, 'V4 就诊人', 'RESIDENT_ID_CARD', '110105199001010010',
                    '13800138000', 'payment-v4@example.com', 1, 'DOC000001', '张医生', '主任医师', 1, '内科',
                    1, '示例市第一人民医院', '示例市健康路 1 号', '2099-01-01', '08:00:00', '12:00:00',
                    ?, '2098-12-31 08:15:00')
                """, appointmentStatus);
        jdbc.update("""
                INSERT INTO payment_orders (payment_no, appointment_order_id, user_id, status)
                VALUES ('PAYV4', 1, 1, ?)
                """, paymentStatus);
    }
}

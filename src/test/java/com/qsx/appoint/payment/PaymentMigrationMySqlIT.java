package com.qsx.appoint.payment;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PaymentMigrationMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appoint")
            .withUsername("appoint")
            .withPassword("appoint-test");

    @Test
    void activePreRefactorPaymentStopsV4OnMySqlWithoutChangingOrdersOrAddingTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3")).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertActiveOrder(jdbc);

        Flyway full = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("4").load();
        assertThatThrownBy(full::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class).hasStackTraceContaining("旧活跃支付");

        assertThat(jdbc.queryForObject("SELECT status FROM appointment_orders WHERE appointment_no = 'APV4MYSQL'", String.class))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE payment_no = 'PAYV4MYSQL'", String.class))
                .isEqualTo("UNPAID");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'payment_orders' AND column_name = 'amount'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'payment_attempts'
                """, Integer.class)).isZero();
    }

    private void insertActiveOrder(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO users (email, password_hash) VALUES ('payment-v4-mysql@example.com', 'unused')");
        jdbc.update("""
                INSERT INTO patients (user_id, name, document_type, document_no, contact_phone, contact_email)
                VALUES (1, 'V4 MySQL 就诊人', 'RESIDENT_ID_CARD', '110105199001010010', '13800138000',
                    'payment-v4-mysql@example.com')
                """);
        jdbc.update("""
                INSERT INTO appointment_orders (
                    appointment_no, user_id, schedule_id, patient_id, patient_name,
                    patient_document_type, patient_document_no, patient_contact_phone, patient_contact_email,
                    doctor_id, doctor_no, doctor_name, doctor_title, department_id, department_name,
                    hospital_id, hospital_name, hospital_address, appointment_date, start_time, end_time,
                    status, payment_expires_at)
                VALUES ('APV4MYSQL', 1, 4, 1, 'V4 MySQL 就诊人', 'RESIDENT_ID_CARD',
                    '110105199001010010', '13800138000', 'payment-v4-mysql@example.com',
                    1, 'DOC000001', '张医生', '主任医师', 1, '内科', 1, '示例市第一人民医院',
                    '示例市健康路 1 号', '2099-01-01', '08:00:00', '12:00:00',
                    'PENDING_PAYMENT', '2098-12-31 08:15:00')
                """);
        jdbc.update("""
                INSERT INTO payment_orders (payment_no, appointment_order_id, user_id, status)
                VALUES ('PAYV4MYSQL', 1, 1, 'UNPAID')
                """);
    }
}


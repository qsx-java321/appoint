package com.qsx.appoint.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.qsx.appoint.support.MySqlConstraintAssertions.assertCheckViolation;

/** 类独占MySQL验证支付可靠性表的唯一键、CHECK及扫描索引；失败后核对原数据。 */
@Testcontainers
class PaymentPersistenceMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appoint")
            .withUsername("appoint")
            .withPassword("appoint-test");

    @Test
    void v6AndV7CreateReliableStoresAndEnforceTheirInvariants() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isPositive();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertNewPaymentOrder(jdbc);

        assertCheckViolation(() -> jdbc.update(
                "UPDATE payment_orders SET amount = NULL WHERE id = 900"), "ck_payment_orders_new_fields");

        jdbc.update("""
                INSERT INTO payment_attempts (
                    id, attempt_no, payment_order_id, channel, status, created_at, updated_at)
                VALUES (900, 'ATTEMPT900', 900, 'MOCK', 'CREATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment_attempts (
                    id, attempt_no, payment_order_id, channel, status, created_at, updated_at)
                VALUES (901, 'ATTEMPT900', 900, 'MOCK', 'CREATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertCheckViolation(() -> jdbc.update("""
                INSERT INTO payment_attempts (
                    id, attempt_no, payment_order_id, channel, status, created_at, updated_at)
                VALUES (902, 'ATTEMPT902', 900, 'MOCK', 'UNKNOWN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """), "ck_payment_attempts_status");

        jdbc.update("""
                INSERT INTO payment_refunds (
                    id, refund_no, payment_order_id, payment_attempt_id, amount, currency, channel,
                    status, reason, attempt_count, created_at, updated_at)
                VALUES (900, 'REFUND900', 900, 900, 50.00, 'CNY', 'MOCK',
                    'PENDING', 'USER_CANCELLED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment_refunds (
                    id, refund_no, payment_order_id, payment_attempt_id, amount, currency, channel,
                    status, reason, attempt_count, created_at, updated_at)
                VALUES (901, 'REFUND901', 900, 900, 50.00, 'CNY', 'MOCK',
                    'PENDING', 'USER_CANCELLED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        jdbc.update("""
                INSERT INTO payment_notifications (
                    channel, notification_id, payload_hash, verification_result, received_at)
                VALUES ('MOCK', 'NOTICE900', 'hash-900', 'VERIFIED', CURRENT_TIMESTAMP)
                """);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment_notifications (
                    channel, notification_id, payload_hash, verification_result, received_at)
                VALUES ('MOCK', 'NOTICE900', 'hash-900', 'VERIFIED', CURRENT_TIMESTAMP)
                """)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);

        jdbc.update("""
                INSERT INTO payment_outbox_events (
                    event_id, aggregate_type, aggregate_id, event_type, event_version, payload,
                    occurred_at, status, publish_attempts, next_attempt_at)
                VALUES ('00000000-0000-0000-0000-000000000900', 'PaymentOrder', 900,
                    'PaymentSucceeded', 1, '{}', CURRENT_TIMESTAMP, 'PENDING', 0, CURRENT_TIMESTAMP)
                """);
        assertCheckViolation(() -> jdbc.update("""
                INSERT INTO payment_outbox_events (
                    event_id, aggregate_type, aggregate_id, event_type, event_version, payload,
                    occurred_at, status, publish_attempts, next_attempt_at)
                VALUES ('00000000-0000-0000-0000-000000000901', 'PaymentOrder', 900,
                    'PaymentSucceeded', 0, '{}', CURRENT_TIMESTAMP, 'PENDING', 0, CURRENT_TIMESTAMP)
                """), "ck_payment_outbox_counters");

        jdbc.update("""
                INSERT INTO coordination_inbox_events (consumer_name, event_id, event_type, processed_at)
                VALUES ('appointment-confirmation', '00000000-0000-0000-0000-000000000900',
                    'PaymentSucceeded', CURRENT_TIMESTAMP)
                """);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO coordination_inbox_events (consumer_name, event_id, event_type, processed_at)
                VALUES ('appointment-confirmation', '00000000-0000-0000-0000-000000000900',
                    'PaymentSucceeded', CURRENT_TIMESTAMP)
                """)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        jdbc.update("""
                INSERT INTO coordination_inbox_events (consumer_name, event_id, event_type, processed_at)
                VALUES ('audit-projection', '00000000-0000-0000-0000-000000000900',
                    'PaymentSucceeded', CURRENT_TIMESTAMP)
                """);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM coordination_inbox_events", Integer.class)).isEqualTo(2);
        // 每次拒绝都必须保留原金额且不留下半条记录，不能把其他SQL错误当成约束生效。
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_orders WHERE id=900", java.math.BigDecimal.class))
                .isEqualByComparingTo("50.00");
        for (String table : java.util.List.of("payment_attempts", "payment_refunds", "payment_notifications", "payment_outbox_events")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).as(table).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM payment_attempts WHERE id=900", String.class)).isEqualTo("CREATED");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'payment_outbox_events'
                    AND index_name = 'idx_payment_outbox_scan'
                """, Integer.class)).isEqualTo(4);
    }

    private void insertNewPaymentOrder(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (900, 'schema@example.com', 'unused')");
        jdbc.update("""
                INSERT INTO patients (
                    id, user_id, name, document_type, document_no, contact_phone, contact_email)
                VALUES (900, 900, '约束就诊人', 'RESIDENT_ID_CARD', '110105199001010010',
                    '13800138000', 'schema@example.com')
                """);
        jdbc.update("""
                INSERT INTO appointment_orders (
                    id, appointment_no, user_id, schedule_id, patient_id, patient_name,
                    patient_document_type, patient_document_no, patient_contact_phone, patient_contact_email,
                    doctor_id, doctor_no, doctor_name, doctor_title, department_id, department_name,
                    hospital_id, hospital_name, hospital_address, appointment_date, start_time, end_time,
                    status, payment_expires_at, fee_amount, fee_currency)
                VALUES (900, 'APSCHEMA900', 900, 4, 900, '约束就诊人', 'RESIDENT_ID_CARD',
                    '110105199001010010', '13800138000', 'schema@example.com', 1, 'DOC000001',
                    '张医生', '主任医师', 1, '内科', 1, '示例市第一人民医院', '示例市健康路 1 号',
                    '2099-01-01', '08:00:00', '12:00:00', 'PENDING_PAYMENT',
                    '2098-12-31 08:15:00', 50.00, 'CNY')
                """);
        jdbc.update("""
                INSERT INTO payment_orders (
                    id, payment_no, appointment_order_id, user_id, status, amount, currency, expires_at, legacy)
                VALUES (900, 'PAYSCHEMA900', 900, 900, 'UNPAID', 50.00, 'CNY',
                    '2098-12-31 08:15:00', FALSE)
                """);
    }
}


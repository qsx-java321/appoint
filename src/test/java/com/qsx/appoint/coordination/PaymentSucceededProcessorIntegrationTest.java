package com.qsx.appoint.coordination;

import com.qsx.appoint.coordination.application.PaymentSucceededEvent;
import com.qsx.appoint.coordination.application.PaymentSucceededProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paid-confirmation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.messaging.enabled=false"
})
class PaymentSucceededProcessorIntegrationTest {
    @Autowired private PaymentSucceededProcessor processor;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void inboxAndConfirmationCommitOnceForDuplicateEvent() {
        fixture();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                "00000000-0000-0000-0000-000000001101", 1, LocalDateTime.of(2098, 12, 1, 8, 0),
                11_004L, 11_003L, 11_005L, new BigDecimal("50.00"), "CNY", "MOCK",
                LocalDateTime.of(2098, 12, 1, 8, 0));

        assertThat(processor.process(event)).isEqualTo("CONFIRMED");
        assertThat(processor.process(event)).isEqualTo("DUPLICATE");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM appointment_orders WHERE id = 11003", String.class)).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visit_number FROM appointment_orders WHERE id = 11003", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT held_capacity FROM schedules WHERE id = 4", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT confirmed_capacity FROM schedules WHERE id = 4", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_visit_number FROM schedules WHERE id = 4", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coordination_inbox_events WHERE event_id = ?", Integer.class,
                event.eventId())).isEqualTo(1);
    }

    private void fixture() {
        jdbcTemplate.update("""
                UPDATE schedules SET status='OPEN', session_type='MORNING', start_time='08:00:00', end_time='12:00:00',
                  total_capacity=2,
                  available_capacity=1, held_capacity=1, confirmed_capacity=0, next_visit_number=1,
                  registration_fee=50.00, fee_currency='CNY' WHERE id=4
                """);
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (11001,'paid-confirm@example.com','hash')");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (11002,11001,'测试','RESIDENT_ID_CARD','110105199001011101','13800000000','p@example.com')
                """);
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency)
                VALUES (11003,'A11003',11001,4,11002,'测试','RESIDENT_ID_CARD','110105199001011101',
                        '13800000000','p@example.com',1,'DOC000001','张医生','主任医师',1,'内科',1,
                        '示例市第一人民医院','示例市健康路 1 号','2099-01-01','09:00:00','09:30:00',
                        'PENDING_PAYMENT','2098-12-01 08:15:00',50.00,'CNY')
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (11004,'P11004',11003,11001,'PAID',50.00,'CNY','2098-12-01 08:15:00',FALSE)
                """);
    }
}

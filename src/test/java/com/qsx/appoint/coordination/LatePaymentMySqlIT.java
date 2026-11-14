package com.qsx.appoint.coordination;

import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.coordination.application.PaymentSucceededEvent;
import com.qsx.appoint.coordination.application.PaymentSucceededProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.qsx.appoint.support.CommittedFixtureScope;
import com.qsx.appoint.support.TestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.assertThat;

/** 类独占MySQL验证迟到事件、取消和过期竞争只释放一次并形成唯一退款；每例清理提交夹具。 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@RegressionTest(properties = {"appoint.messaging.enabled=false", "appoint.expiration.enabled=false"})
class LatePaymentMySqlIT {
    private CommittedFixtureScope fixtureScope;

    @BeforeEach
    void trackCommittedFixture() { fixtureScope = new CommittedFixtureScope(jdbcTemplate); }

    @AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-lock-wait-timeout=10").withDatabaseName("appoint").withUsername("appoint").withPassword("appoint-test");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PaymentSucceededProcessor processor;
    @Autowired private BookingLifecycleCoordinator lifecycle;

    @Test
    void lateEventsCancellationAndExpirySerializeToOneReleaseAndRefund() throws Exception {
        fixture();
        jdbcTemplate.update("UPDATE appointment_orders SET payment_expires_at='2020-01-01 08:15:00' WHERE id=11003");
        jdbcTemplate.update("UPDATE payment_orders SET paid_at='2020-01-01 08:15:00' WHERE id=11004");
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = new TestExecutor(4)) {
            var actions = java.util.List.<java.util.concurrent.Callable<Object>>of(
                    () -> processor.process(event()), () -> processor.process(event()),
                    () -> lifecycle.cancelOwned(11001L, 11003L), () -> lifecycle.expireIfDue(11003L));
            var futures = actions.stream().map(action -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("启动屏障超时");
                return action.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003", String.class)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT available_capacity FROM schedules WHERE id=4", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT held_capacity+confirmed_capacity FROM schedules WHERE id=4", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointment_active_claims WHERE appointment_order_id=11003", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=11004", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=11004 AND event_type='RefundRequested'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coordination_inbox_events", Integer.class)).isEqualTo(2);
    }

    private PaymentSucceededEvent event() {
        LocalDateTime paidAt = LocalDateTime.of(2020, 1, 1, 8, 15);
        return new PaymentSucceededEvent(UUID.randomUUID().toString(), 1, paidAt, 11004L, 11003L,
                11005L, new BigDecimal("50.00"), "CNY", "MOCK", paidAt);
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
                INSERT INTO appointment_active_claims
                  (schedule_id, appointment_order_id, patient_id, document_type, document_no)
                VALUES (4, 11003, 11002, 'RESIDENT_ID_CARD', '110105199001011101')
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (11004,'P11004',11003,11001,'PAID',50.00,'CNY','2098-12-01 08:15:00',FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO payment_attempts
                  (id,attempt_no,payment_order_id,channel,provider_order_no,provider_transaction_no,
                   status,created_at,processing_at,completed_at,updated_at)
                VALUES (11005,'PA11005',11004,'MOCK','PO11005','PT11005','SUCCEEDED',
                        '2098-12-01 08:00:00','2098-12-01 08:00:00','2098-12-01 08:15:00',
                        '2098-12-01 08:15:00')
                """);
    }
}

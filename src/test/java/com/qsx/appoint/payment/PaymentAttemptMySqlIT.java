package com.qsx.appoint.payment;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.payment.application.PaymentAttemptService;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import com.qsx.appoint.support.CommittedFixtureScope;
import com.qsx.appoint.support.TestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/** 类独占MySQL验证并发发起只产生一笔有效尝试；保留独立线程中的真实提交。 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@RegressionTest(properties = "appoint.expiration.enabled=false")
@ActiveProfiles("regression")
class PaymentAttemptMySqlIT {
    private CommittedFixtureScope fixtureScope;

    @BeforeEach
    void trackCommittedFixture() { fixtureScope = new CommittedFixtureScope(jdbcTemplate); }

    @AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-lock-wait-timeout=10").withDatabaseName("appoint").withUsername("appoint").withPassword("appoint-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired private PaymentAttemptService service;
    @Autowired private PaymentAttemptMapper attemptMapper;
    @Autowired private PaymentOrderMapper paymentMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentStartsCreateOnlyOneActiveAttempt() throws Exception {
        long userId = 9_100_001L;
        long patientId = 9_100_002L;
        long appointmentId = 9_100_003L;
        long paymentId = 9_100_004L;
        fixture(userId, patientId, appointmentId, paymentId);

        List<Object> results = race(
                () -> service.startAttempt(userId, paymentId, PaymentChannel.MOCK),
                () -> service.startAttempt(userId, paymentId, PaymentChannel.MOCK));

        assertThat(results.stream().filter(PaymentAttemptResponse.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(BusinessException.class::isInstance)).hasSize(1);
        assertThat(attemptMapper.selectByPaymentOrderIdForUpdate(paymentId)).hasSize(1);
        assertThat(paymentMapper.selectById(paymentId).getStatus()).isEqualTo("PAYING");
    }

    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = new TestExecutor(2)) {
            Future<Object> a = executor.submit(() -> call(ready, start, first));
            Future<Object> b = executor.submit(() -> call(ready, start, second));
            if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("PaymentAttemptMySqlIT: ready屏障超时");
            start.countDown();
            return List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private Object call(CountDownLatch ready, CountDownLatch start, Callable<Object> action) throws Exception {
        ready.countDown();
        if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("PaymentAttemptMySqlIT: start屏障超时");
        try {
            return action.call();
        } catch (BusinessException exception) {
            return exception;
        }
    }

    private void fixture(long userId, long patientId, long appointmentId, long paymentId) {
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (?,?,?)",
                userId, "mysql-attempt@example.com", "hash");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (?,?,?,'RESIDENT_ID_CARD','110105199001010010','13800000000','p@example.com')
                """, patientId, userId, "测试");
        LocalDateTime expiresAt = LocalDateTime.of(2099, 1, 1, 8, 15);
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency)
                VALUES (?,?,?,1,?,'测试','RESIDENT_ID_CARD','110105199001010010','13800000000','p@example.com',
                        1,'DOC000001','张医生','主任医师',1,'内科',1,'示例市第一人民医院','示例市健康路 1 号',
                        '2099-01-01','09:00:00','09:30:00','PENDING_PAYMENT',?,50.00,'CNY')
                """, appointmentId, "A" + appointmentId, userId, patientId, expiresAt);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (?,?,?,?,'UNPAID',50.00,'CNY',?,FALSE)
                """, paymentId, "P" + paymentId, appointmentId, userId, expiresAt);
    }
}

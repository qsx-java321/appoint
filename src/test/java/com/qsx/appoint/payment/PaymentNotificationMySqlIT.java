package com.qsx.appoint.payment;

import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.application.PaymentAttemptService;
import com.qsx.appoint.payment.application.PaymentNotificationIngress;
import com.qsx.appoint.payment.application.PaymentNotificationService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;
import com.qsx.appoint.payment.dto.PaymentNotificationResponse;
import com.qsx.appoint.payment.mapper.PaymentNotificationMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import com.qsx.appoint.payment.port.VerifiedPaymentNotification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@SpringBootTest(properties = "appoint.expiration.enabled=false")
@ActiveProfiles("test")
class PaymentNotificationMySqlIT {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate fixtureJdbc;
    private com.qsx.appoint.support.CommittedFixtureScope fixtureScope;

    @org.junit.jupiter.api.BeforeEach
    void trackCommittedFixture() { fixtureScope = new com.qsx.appoint.support.CommittedFixtureScope(fixtureJdbc); }

    @org.junit.jupiter.api.AfterEach
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

    @Autowired private PaymentAttemptService attemptService;
    @Autowired private PaymentNotificationService notificationService;
    @Autowired private PaymentNotificationIngress notificationIngress;
    @Autowired private MockPaymentGateway gateway;
    @Autowired private PaymentNotificationMapper notificationMapper;
    @Autowired private PaymentOrderMapper paymentMapper;
    @Autowired private PaymentOutboxEventMapper outboxMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDuplicateSuccessHasOneMoneyEffectAndStableEvent() throws Exception {
        long userId = 9_200_001L;
        long patientId = 9_200_002L;
        long appointmentId = 9_200_003L;
        long paymentId = 9_200_004L;
        fixture(userId, patientId, appointmentId, paymentId);
        PaymentAttemptResponse attempt = attemptService.startAttempt(userId, paymentId, PaymentChannel.MOCK);
        RawPaymentNotification raw = gateway.complete(
                attempt.providerOrderNo(), "mysql-notify-success", LocalDateTime.now());
        VerifiedPaymentNotification verified = gateway.verifyNotification(raw);
        String payloadHash = sha256(verified.toString());

        List<PaymentNotificationResponse> results = race(
                () -> notificationService.handle(PaymentChannel.MOCK, verified, payloadHash),
                () -> notificationService.handle(PaymentChannel.MOCK, verified, payloadHash));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(PaymentNotificationResponse::processingResult)
                .containsOnly("SUCCEEDED");
        assertThat(results).extracting(PaymentNotificationResponse::duplicate)
                .containsExactlyInAnyOrder(false, true);
        assertThat(paymentMapper.selectById(paymentId).getStatus()).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_notifications WHERE notification_id=?", Integer.class,
                "mysql-notify-success")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=? AND event_type='PaymentSucceeded'",
                Integer.class, paymentId)).isEqualTo(1);
    }

    @Test
    void forgedSignatureDoesNotChangeMoneyState() {
        AttemptContext context = start(9_210_000L);
        RawPaymentNotification signed = gateway.complete(context.attempt().providerOrderNo(),
                "mysql-forged", LocalDateTime.now());
        RawPaymentNotification forged = new RawPaymentNotification(signed.notificationId(),
                signed.providerOrderNo(), signed.providerTransactionNo(), signed.status(), signed.amount(),
                signed.currency(), signed.occurredAt(), signed.signature() + "00");

        assertThatThrownBy(() -> notificationIngress.handle(PaymentChannel.MOCK, forged))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_SIGNATURE_INVALID));
        assertThat(paymentMapper.selectById(context.paymentId()).getStatus()).isEqualTo("PAYING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_notifications WHERE notification_id='mysql-forged'", Integer.class))
                .isZero();
    }

    @Test
    void amountOrCurrencyMismatchIsAuditedWithoutChangingMoneyState() {
        AttemptContext context = start(9_220_000L);
        RawPaymentNotification wrongAmount = gateway.signedNotification("mysql-wrong-amount",
                context.attempt().providerOrderNo(), "wrong-amount-txn", com.qsx.appoint.payment.port.GatewayPaymentStatus.SUCCEEDED,
                new BigDecimal("51.00"), "CNY", LocalDateTime.now());

        PaymentNotificationResponse response = notificationIngress.handle(PaymentChannel.MOCK, wrongAmount);

        assertThat(response.processingResult()).isEqualTo("REJECTED_AMOUNT_OR_CURRENCY");
        assertThat(paymentMapper.selectById(context.paymentId()).getStatus()).isEqualTo("PAYING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=?", Integer.class,
                context.paymentId())).isZero();
    }

    @Test
    void sameNotificationIdWithDifferentPayloadIsRejected() {
        AttemptContext context = start(9_230_000L);
        RawPaymentNotification failed = gateway.fail(context.attempt().providerOrderNo(),
                "mysql-conflicting-notice", LocalDateTime.now());
        assertThat(notificationIngress.handle(PaymentChannel.MOCK, failed).processingResult()).isEqualTo("FAILED");
        RawPaymentNotification succeeded = gateway.complete(context.attempt().providerOrderNo(),
                "mysql-conflicting-notice", LocalDateTime.now().plusSeconds(1));

        assertThatThrownBy(() -> notificationIngress.handle(PaymentChannel.MOCK, succeeded))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(paymentMapper.selectById(context.paymentId()).getStatus()).isEqualTo("UNPAID");
    }

    @Test
    void failedAttemptCanRetryAndLaterSuccessWins() {
        AttemptContext context = start(9_240_000L);
        RawPaymentNotification failed = gateway.fail(context.attempt().providerOrderNo(),
                "mysql-first-failed", LocalDateTime.now());
        assertThat(notificationIngress.handle(PaymentChannel.MOCK, failed).processingResult()).isEqualTo("FAILED");
        PaymentAttemptResponse retry = attemptService.startAttempt(context.userId(), context.paymentId(),
                PaymentChannel.MOCK);
        RawPaymentNotification succeeded = gateway.complete(retry.providerOrderNo(),
                "mysql-retry-success", LocalDateTime.now().plusSeconds(1));

        assertThat(notificationIngress.handle(PaymentChannel.MOCK, succeeded).processingResult()).isEqualTo("SUCCEEDED");
        assertThat(paymentMapper.selectById(context.paymentId()).getStatus()).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_attempts WHERE payment_order_id=?", Integer.class,
                context.paymentId())).isEqualTo(2);
    }

    @Test
    void lateFailureCannotOverwriteSuccessfulMoneyFact() {
        AttemptContext context = start(9_250_000L);
        RawPaymentNotification succeeded = gateway.complete(context.attempt().providerOrderNo(),
                "mysql-success-first", LocalDateTime.now());
        assertThat(notificationIngress.handle(PaymentChannel.MOCK, succeeded).processingResult()).isEqualTo("SUCCEEDED");
        RawPaymentNotification lateFailure = gateway.signedNotification("mysql-late-failure",
                context.attempt().providerOrderNo(), succeeded.providerTransactionNo(),
                com.qsx.appoint.payment.port.GatewayPaymentStatus.FAILED,
                new BigDecimal("50.00"), "CNY", LocalDateTime.now().plusSeconds(1));

        assertThat(notificationIngress.handle(PaymentChannel.MOCK, lateFailure).processingResult())
                .isEqualTo("IGNORED_LATE_FAILURE");
        assertThat(paymentMapper.selectById(context.paymentId()).getStatus()).isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=? AND event_type='PaymentSucceeded'",
                Integer.class, context.paymentId())).isEqualTo(1);
    }

    private AttemptContext start(long base) {
        long userId = base + 1;
        long patientId = base + 2;
        long appointmentId = base + 3;
        long paymentId = base + 4;
        fixture(userId, patientId, appointmentId, paymentId);
        return new AttemptContext(userId, paymentId,
                attemptService.startAttempt(userId, paymentId, PaymentChannel.MOCK));
    }

    private List<PaymentNotificationResponse> race(Callable<PaymentNotificationResponse> first,
                                                    Callable<PaymentNotificationResponse> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = new com.qsx.appoint.support.TestExecutor(2)) {
            Future<PaymentNotificationResponse> a = executor.submit(() -> call(ready, start, first));
            Future<PaymentNotificationResponse> b = executor.submit(() -> call(ready, start, second));
            if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("PaymentNotificationMySqlIT: ready屏障超时");
            start.countDown();
            return List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS), b.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private PaymentNotificationResponse call(CountDownLatch ready, CountDownLatch start,
                                             Callable<PaymentNotificationResponse> action) throws Exception {
        ready.countDown();
        if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("PaymentNotificationMySqlIT: start屏障超时");
        return action.call();
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void fixture(long userId, long patientId, long appointmentId, long paymentId) {
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (?,?,?)",
                userId, "mysql-notification-" + userId + "@example.com", "hash");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (?,?,?,'RESIDENT_ID_CARD',?,'13800000000','p@example.com')
                """, patientId, userId, "测试", "MYSQL-DOC-" + patientId);
        LocalDateTime expiresAt = LocalDateTime.of(2099, 1, 1, 8, 15);
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency)
                VALUES (?,?,?,1,?,'测试','RESIDENT_ID_CARD','110105199001010011','13800000000','p@example.com',
                        1,'DOC000001','张医生','主任医师',1,'内科',1,'示例市第一人民医院','示例市健康路 1 号',
                        '2099-01-01','09:00:00','09:30:00','PENDING_PAYMENT',?,50.00,'CNY')
                """, appointmentId, "A" + appointmentId, userId, patientId, expiresAt);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (?,?,?,?,'UNPAID',50.00,'CNY',?,FALSE)
                """, paymentId, "P" + paymentId, appointmentId, userId, expiresAt);
    }

    private record AttemptContext(long userId, long paymentId, PaymentAttemptResponse attempt) {
    }
}

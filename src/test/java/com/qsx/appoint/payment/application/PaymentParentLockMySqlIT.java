package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.domain.RefundReason;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;
import com.qsx.appoint.payment.port.GatewayRefundRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties={"appoint.messaging.enabled=false", "appoint.expiration.enabled=false"})
@ActiveProfiles("test")
class PaymentParentLockMySqlIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appoint").withUsername("appoint").withPassword("appoint-test");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
    private static final AtomicLong SEQUENCE = new AtomicLong(9600000);
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private PaymentAttemptService attempts;
    @Autowired private PaymentAttemptTransaction attemptTransaction;
    @Autowired private PaymentNotificationIngress notifications;
    @Autowired private PaymentRefundOperations refunds;
    @Autowired private RefundWorker worker;
    @MockitoSpyBean private MockPaymentGateway gateway;
    @MockitoSpyBean private com.qsx.appoint.payment.port.PaymentGatewayRegistry registry;

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void attemptT2LocksParentBeforeWaitingForChild(boolean failure) throws Exception {
        Context context = start();
        jdbcTemplate.update("UPDATE payment_attempts SET status='CREATED' WHERE id=?", context.attempt().paymentAttemptId());
        try (var pool = Executors.newSingleThreadExecutor(); var child = lockChild("payment_attempts", context.attempt().paymentAttemptId())) {
            var future = pool.submit(() -> {
                if (failure) attemptTransaction.markFailed(context.attempt().paymentAttemptId(), "TEST", "test failure");
                else attemptTransaction.markProcessing(context.attempt().paymentAttemptId(), context.attempt().providerOrderNo());
            });
            await().atMost(Duration.ofSeconds(10)).until(() -> parentIsLocked(context.paymentId()));
            assertThat(future.isDone()).isFalse();
            child.rollback();
            future.get(10, TimeUnit.SECONDS);
        }
        assertThat(status("payment_attempts", context.attempt().paymentAttemptId())).isEqualTo(failure ? "FAILED" : "PROCESSING");
    }

    @Test
    void callbackLocksParentBeforeWaitingForAttempt() throws Exception {
        Context context = start();
        var raw = gateway.complete(context.attempt().providerOrderNo(), UUID.randomUUID().toString(), LocalDateTime.now());
        try (var pool = Executors.newSingleThreadExecutor(); var child = lockChild("payment_attempts", context.attempt().paymentAttemptId())) {
            var future = pool.submit(() -> notifications.handle(PaymentChannel.MOCK, raw));
            await().atMost(Duration.ofSeconds(10)).until(() -> parentIsLocked(context.paymentId()));
            assertThat(future.isDone()).isFalse();
            child.rollback();
            assertThat(future.get(10, TimeUnit.SECONDS).processingResult()).isEqualTo("SUCCEEDED");
        }
        assertThat(status("payment_orders", context.paymentId())).isEqualTo("PAID");
    }

    @Test
    void refundClaimLocksParentBeforeWaitingForRefund() throws Exception {
        Context context = paid();
        refunds.requestFullRefund(context.paymentId(), RefundReason.APPOINTMENT_CANCELLED);
        RefundRequestedEvent event = refundEvent(context.paymentId());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(gateway).refund(any(GatewayRefundRequest.class));
        try (var pool = Executors.newSingleThreadExecutor(); var child = lockChild("payment_refunds", event.refundId())) {
            var future = pool.submit(() -> worker.process(event));
            await().atMost(Duration.ofSeconds(10)).until(() -> parentIsLocked(context.paymentId()));
            assertThat(future.isDone()).isFalse();
            child.rollback();
            assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("SUCCEEDED");
        }
        assertThat(status("payment_orders", context.paymentId())).isEqualTo("REFUNDED");
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void refundCompletionAndFailureLockParentBeforeChild(boolean failure) throws Exception {
        Context context = paid();
        refunds.requestFullRefund(context.paymentId(), RefundReason.APPOINTMENT_CANCELLED);
        RefundRequestedEvent event = refundEvent(context.paymentId());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("渠道屏障超时");
            if (failure) throw new IllegalStateException("渠道故障");
            return invocation.callRealMethod();
        }).when(gateway).refund(any(GatewayRefundRequest.class));
        try (var pool = Executors.newSingleThreadExecutor()) {
            var future = pool.submit(() -> worker.process(event));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                try (var child = lockChild("payment_refunds", event.refundId())) {
                    release.countDown();
                    await().atMost(Duration.ofSeconds(10)).until(() -> parentIsLocked(context.paymentId()));
                    assertThat(future.isDone()).isFalse();
                    child.rollback();
                }
                if (failure) assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
                else assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("SUCCEEDED");
            } finally { release.countDown(); }
        }
        assertThat(status("payment_refunds", event.refundId())).isEqualTo(failure ? "FAILED" : "SUCCEEDED");
        assertThat(status("payment_orders", context.paymentId())).isEqualTo(failure ? "REFUND_PENDING" : "REFUNDED");
    }

    @Test
    void duplicateWorkersAndRefundRequestKeepSuccessWhenSlowerCallFails() throws Exception {
        Context context = paid();
        refunds.requestFullRefund(context.paymentId(), RefundReason.APPOINTMENT_CANCELLED);
        RefundRequestedEvent event = refundEvent(context.paymentId());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        // 通过port注入交错；MockPay.refund本身synchronized，不能在其monitor内等待另一调用。
        var concurrentGateway=mock(com.qsx.appoint.payment.port.PaymentGateway.class,
                org.mockito.AdditionalAnswers.delegatesTo(gateway));
        doReturn(concurrentGateway).when(registry).require(PaymentChannel.MOCK);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("渠道屏障超时");
                throw new IllegalStateException("较早请求晚到失败");
            }
            return gateway.refund(invocation.getArgument(0));
        }).when(concurrentGateway).refund(any(GatewayRefundRequest.class));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> worker.process(event));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                refunds.requestFullRefund(context.paymentId(), RefundReason.APPOINTMENT_CANCELLED);
                assertThat(pool.submit(() -> worker.process(event)).get(10, TimeUnit.SECONDS)).isEqualTo("SUCCEEDED");
                release.countDown();
                assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            } finally { release.countDown(); }
        }
        assertThat(status("payment_refunds", event.refundId())).isEqualTo("SUCCEEDED");
        assertThat(status("payment_orders", context.paymentId())).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=?", Integer.class, context.paymentId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=? AND event_type='RefundSucceeded'", Integer.class, context.paymentId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=? AND event_type='RefundFailed'", Integer.class, context.paymentId())).isZero();
    }

    @Test
    void refundRequestLocksParentBeforeSuccessfulAttempt() throws Exception {
        Context context=paid();
        try (var pool=Executors.newSingleThreadExecutor(); var child=lockChild("payment_attempts",context.attempt().paymentAttemptId())) {
            var future=pool.submit(() -> refunds.requestFullRefund(context.paymentId(),RefundReason.APPOINTMENT_CANCELLED));
            await().atMost(Duration.ofSeconds(10)).until(() -> parentIsLocked(context.paymentId()));
            assertThat(future.isDone()).isFalse();
            child.rollback();
            future.get(10,TimeUnit.SECONDS);
        }
        assertThat(status("payment_orders",context.paymentId())).isEqualTo("REFUND_PENDING");
    }

    @Test
    void callbackAndRefundRequestHaveOneMoneyFactAndOneRefund() throws Exception {
        Context context=paid();
        var duplicate=gateway.complete(context.attempt().providerOrderNo(),UUID.randomUUID().toString(),LocalDateTime.now());
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            var callback=pool.submit(() -> {
                ready.countDown();
                if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("启动超时");
                return notifications.handle(PaymentChannel.MOCK,duplicate);
            });
            var refund=pool.submit(() -> {
                ready.countDown();
                if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("启动超时");
                refunds.requestFullRefund(context.paymentId(),RefundReason.APPOINTMENT_CANCELLED);
                return "REFUND";
            });
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(callback.get(10,TimeUnit.SECONDS).processingResult()).isIn("ALREADY_SUCCEEDED","REJECTED_STATE");
            refund.get(10,TimeUnit.SECONDS);
        }
        assertThat(status("payment_orders",context.paymentId())).isEqualTo("REFUND_PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=?",Integer.class,context.paymentId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=? AND event_type='PaymentSucceeded'",Integer.class,context.paymentId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=? AND event_type='RefundRequested'",Integer.class,context.paymentId())).isEqualTo(1);
    }

    @org.junit.jupiter.api.BeforeEach
    void assertChannelCreationOutsideTransaction() {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(gateway).createPayment(any(com.qsx.appoint.payment.port.GatewayPaymentRequest.class));
    }

    private Context start() {
        long base = SEQUENCE.addAndGet(10);
        fixture(base+1,base+2,base+3,base+4);
        return new Context(base+1,base+4,attempts.startAttempt(base+1,base+4,PaymentChannel.MOCK));
    }
    private Context paid() {
        Context context = start();
        notifications.handle(PaymentChannel.MOCK, gateway.complete(context.attempt().providerOrderNo(), UUID.randomUUID().toString(),LocalDateTime.now()));
        return context;
    }
    private RefundRequestedEvent refundEvent(Long paymentId) {
        return jdbcTemplate.queryForObject("SELECT id,refund_no FROM payment_refunds WHERE payment_order_id=?",
                (rs,n) -> new RefundRequestedEvent(UUID.randomUUID().toString(),1,rs.getLong(1),rs.getString(2)),paymentId);
    }
    private String status(String table, Long id) { return jdbcTemplate.queryForObject("SELECT status FROM "+table+" WHERE id=?",String.class,id); }
    private Connection lockChild(String table, Long id) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement("SELECT id FROM "+table+" WHERE id=? FOR UPDATE")) {
            statement.setLong(1,id); statement.executeQuery().close();
        }
        return connection;
    }
    private boolean parentIsLocked(Long id) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT id FROM payment_orders WHERE id=? FOR UPDATE NOWAIT")) {
                statement.setLong(1,id);
                statement.executeQuery().close();
                return false;
            } catch (SQLException exception) {
                if (exception.getErrorCode() == 3572) return true;
                throw exception;
            } finally { connection.rollback(); }
        }
    }
    private record Context(long userId, Long paymentId, PaymentAttemptResponse attempt) { }

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

}

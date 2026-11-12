package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.entity.PaymentNotification;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.mapper.PaymentNotificationMapper;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:notification-atomicity;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.messaging.enabled=false", "appoint.expiration.enabled=false"})
@ActiveProfiles("test")
class PaymentNotificationAtomicityTest {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate fixtureJdbc;
    private com.qsx.appoint.support.CommittedFixtureScope fixtureScope;

    @org.junit.jupiter.api.BeforeEach
    void trackCommittedFixture() { fixtureScope = new com.qsx.appoint.support.CommittedFixtureScope(fixtureJdbc); }

    @org.junit.jupiter.api.AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    private static final AtomicLong IDS = new AtomicLong(9700000);
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PaymentAttemptService attempts;
    @Autowired private PaymentNotificationIngress ingress;
    @Autowired private MockPaymentGateway gateway;
    @MockitoSpyBean private PaymentNotificationMapper notifications;
    @MockitoSpyBean private PaymentOutboxEventMapper outbox;

    @ParameterizedTest
    @ValueSource(strings={"audit","outbox"})
    void writeFailureRollsBackMoneyAttemptAuditAndEventThenAllowsRetry(String failure) {
        long base=IDS.addAndGet(10), paymentId=base+4;
        fixture(base+1,base+2,base+3,paymentId);
        var attempt=attempts.startAttempt(base+1,paymentId,PaymentChannel.MOCK);
        String notificationId=UUID.randomUUID().toString();
        var raw=gateway.complete(attempt.providerOrderNo(),notificationId,LocalDateTime.now());
        if (failure.equals("audit")) doThrow(new IllegalStateException("audit failure")).when(notifications).insert(any(PaymentNotification.class));
        else doThrow(new IllegalStateException("outbox failure")).when(outbox).insert(any(PaymentOutboxEvent.class));
        try {
            assertThatThrownBy(() -> ingress.handle(PaymentChannel.MOCK,raw)).isInstanceOf(IllegalStateException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment_orders WHERE id=?",String.class,paymentId)).isEqualTo("PAYING");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment_attempts WHERE id=?",String.class,attempt.paymentAttemptId())).isEqualTo("PROCESSING");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_notifications WHERE notification_id=?",Integer.class,notificationId)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=?",Integer.class,paymentId)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=?",Integer.class,paymentId)).isZero();
        } finally { reset(notifications,outbox); }
        assertThat(ingress.handle(PaymentChannel.MOCK,raw).processingResult()).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=?",Integer.class,paymentId)).isEqualTo(1);
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

}

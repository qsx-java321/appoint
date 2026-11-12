package com.qsx.appoint.coordination;

import com.qsx.appoint.coordination.application.PaymentSucceededEvent;
import com.qsx.appoint.coordination.application.PaymentSucceededProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spring/H2验证Inbox与业务原子性；普通用例回滚，NOT_SUPPORTED故障窗口保留真实事务及显式清理。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paid-confirmation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.messaging.enabled=false"
})
@Transactional
class PaymentSucceededProcessorIntegrationTest {
    @Autowired private PaymentSucceededProcessor processor;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BookingLifecycleCoordinator lifecycle;
    @Autowired private Clock appointmentClock;

    @Test
    void inboxAndConfirmationCommitOnceForDuplicateEvent() {
        fixture();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                "00000000-0000-0000-0000-000000001101", 1, LocalDateTime.of(2098, 12, 1, 8, 0),
                11_004L, 11_003L, 11_005L, new BigDecimal("50.00"), "CNY", "MOCK",
                LocalDateTime.of(2098, 12, 1, 8, 0));

        assertThat(processor.process(event).name()).isEqualTo("CONFIRMED");
        assertThat(processor.process(event).name()).isEqualTo("DUPLICATE");

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

    @ParameterizedTest
    @CsvSource({"0", "1"})
    void paymentConfirmedAtExactDeadlineIsRefundedWithoutRestoringAppointment(int lateSeconds) {
        fixture();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                "00000000-0000-0000-0000-000000001102", 1, LocalDateTime.of(2098, 12, 1, 8, 15),
                11_004L, 11_003L, 11_005L, new BigDecimal("50.00"), "CNY", "MOCK",
                LocalDateTime.of(2098, 12, 1, 8, 15).plusSeconds(lateSeconds));

        assertThat(processor.process(event).name()).isEqualTo("REFUND_REQUIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM appointment_orders WHERE id = 11003", String.class))
                .isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT held_capacity FROM schedules WHERE id = 4", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT available_capacity FROM schedules WHERE id = 4", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointment_active_claims WHERE appointment_order_id = 11003", Integer.class)).isZero();
        assertThat(processor.process(event).name()).isEqualTo("DUPLICATE");
        PaymentSucceededEvent duplicateFact = new PaymentSucceededEvent(
                "00000000-0000-0000-0000-000000001104", 1, event.occurredAt(), event.paymentOrderId(),
                event.appointmentOrderId(), event.paymentAttemptId(), event.amount(), event.currency(),
                event.channel(), event.paidAt());
        assertThat(processor.process(duplicateFact).name()).isEqualTo("REFUND_REQUIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT available_capacity FROM schedules WHERE id = 4", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id = 11004", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT confirmed_capacity FROM schedules WHERE id = 4", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payment_orders WHERE id = 11004", String.class))
                .isEqualTo("REFUND_PENDING");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedClaimRemovalRollsBackInboxAndInventoryInRealTransaction() {
        fixture();
        jdbcTemplate.update("DELETE FROM appointment_active_claims WHERE appointment_order_id = 11003");
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                "00000000-0000-0000-0000-000000001103", 1, LocalDateTime.of(2098, 12, 1, 8, 15),
                11_004L, 11_003L, 11_005L, new BigDecimal("50.00"), "CNY", "MOCK",
                LocalDateTime.of(2098, 12, 1, 8, 15));
        try {
            assertThatThrownBy(() -> processor.process(event)).isInstanceOf(RuntimeException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003", String.class))
                    .isEqualTo("PENDING_PAYMENT");
            assertThat(jdbcTemplate.queryForObject("SELECT held_capacity FROM schedules WHERE id=4", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM coordination_inbox_events WHERE event_id=?", Integer.class, event.eventId()))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=11004", Integer.class)).isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM payment_attempts WHERE payment_order_id=11004");
            jdbcTemplate.update("DELETE FROM payment_orders WHERE id=11004");
            jdbcTemplate.update("DELETE FROM appointment_orders WHERE id=11003");
            jdbcTemplate.update("DELETE FROM patients WHERE id=11002");
            jdbcTemplate.update("DELETE FROM users WHERE id=11001");
        }
    }

    @ParameterizedTest
    @CsvSource({"CANCELLED", "EXPIRED", "CANCELLED_BY_HOSPITAL"})
    void lateEventForClosedAppointmentOnlyEnsuresRefund(String status) {
        fixture();
        jdbcTemplate.update("UPDATE appointment_orders SET status=? WHERE id=11003", status);
        jdbcTemplate.update("UPDATE schedules SET held_capacity=0,available_capacity=2 WHERE id=4");
        jdbcTemplate.update("DELETE FROM appointment_active_claims WHERE appointment_order_id=11003");
        for (int i = 0; i < 2; i++) {
            PaymentSucceededEvent event = new PaymentSucceededEvent(java.util.UUID.randomUUID().toString(), 1,
                    LocalDateTime.of(2098, 12, 1, 8, 15), 11004L, 11003L, 11005L,
                    new BigDecimal("50.00"), "CNY", "MOCK", LocalDateTime.of(2098, 12, 1, 8, 15));
            assertThat(processor.process(event).name()).isEqualTo("REFUND_REQUIRED");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003", String.class)).isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject("SELECT available_capacity FROM schedules WHERE id=4", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=11004", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=11004 AND event_type='RefundRequested'", Integer.class)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedRefundCreationRollsBackCloseInventoryClaimAndInbox() {
        fixture();
        jdbcTemplate.update("UPDATE payment_orders SET status='UNPAID' WHERE id=11004");
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                "00000000-0000-0000-0000-000000001107", 1, LocalDateTime.of(2098, 12, 1, 8, 15),
                11_004L, 11_003L, 11_005L, new BigDecimal("50.00"), "CNY", "MOCK",
                LocalDateTime.of(2098, 12, 1, 8, 15));
        try {
            assertThatThrownBy(() -> processor.process(event)).isInstanceOf(BusinessException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003", String.class))
                    .isEqualTo("PENDING_PAYMENT");
            assertThat(jdbcTemplate.queryForObject("SELECT held_capacity FROM schedules WHERE id=4", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT available_capacity FROM schedules WHERE id=4", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointment_active_claims WHERE appointment_order_id=11003", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coordination_inbox_events WHERE event_id=?", Integer.class, event.eventId())).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=11004", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=11004", Integer.class)).isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM appointment_active_claims WHERE appointment_order_id=11003");
            jdbcTemplate.update("DELETE FROM payment_attempts WHERE payment_order_id=11004");
            jdbcTemplate.update("DELETE FROM payment_orders WHERE id=11004");
            jdbcTemplate.update("DELETE FROM appointment_orders WHERE id=11003");
            jdbcTemplate.update("DELETE FROM patients WHERE id=11002");
            jdbcTemplate.update("DELETE FROM users WHERE id=11001");
        }
    }

    @ParameterizedTest
    @CsvSource({"UNPAID,false", "PAYING,false", "PAID,true"})
    void pendingCancellationPreservesRefundReasonAndBoolean(String payment, boolean refund) {
        fixture();
        jdbcTemplate.update("UPDATE payment_orders SET status=? WHERE id=11004", payment);
        // 固定未来截止，取消时选择 CANCELLED；资金事实决定是否退款及 HTTP 200/202。
        assertThat(lifecycle.cancelOwned(11001L, 11003L)).isEqualTo(refund);
        assertThat(lifecycle.cancelOwned(11001L, 11003L)).isEqualTo(refund);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003", String.class))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT available_capacity FROM schedules WHERE id=4", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointment_active_claims WHERE appointment_order_id=11003", Integer.class))
                .isZero();
        if (refund) {
            assertThat(jdbcTemplate.queryForObject("SELECT reason FROM payment_refunds WHERE payment_order_id=11004", String.class))
                    .isEqualTo("APPOINTMENT_CANCELLED");
        }
    }

    @ParameterizedTest
    @CsvSource({"2098-12-01 08:14:59,CONFIRMED", "2098-12-01 08:15:00,EXPIRED",
            "2098-12-01 08:15:01,EXPIRED", ",EXPIRED"})
    void reconciliationUsesPaidAtIncludingMissingAndExactDeadline(String paidAt, String expected) {
        fixture();
        jdbcTemplate.update("UPDATE payment_orders SET paid_at=? WHERE id=11004", paidAt);
        assertThat(lifecycle.reconcilePaidPending(11003L)).isTrue();
        assertThat(lifecycle.reconcilePaidPending(11003L)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003", String.class))
                .isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject("SELECT held_capacity FROM schedules WHERE id=4", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT total_capacity-available_capacity-held_capacity-confirmed_capacity FROM schedules WHERE id=4", Integer.class)).isZero();
        if ("EXPIRED".equals(expected)) {
            assertThat(jdbcTemplate.queryForObject("SELECT reason FROM payment_refunds WHERE payment_order_id=11004", String.class))
                    .isEqualTo("APPOINTMENT_NOT_CONFIRMABLE");
        }
    }

    @Test
    void expirationBeforeDeadlineDoesNotRequirePaymentButCancellationChecksOwnerFirst() {
        fixture();
        jdbcTemplate.update("DELETE FROM payment_attempts WHERE payment_order_id=11004");
        jdbcTemplate.update("DELETE FROM payment_orders WHERE id=11004");
        assertThat(lifecycle.expireIfDue(11003L)).isFalse();
        assertThat(lifecycle.expireIfDue(-1L)).isFalse();
        assertThatThrownBy(() -> lifecycle.cancelOwned(999L, 11003L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> lifecycle.cancelOwned(11001L, 11003L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @ParameterizedTest
    @CsvSource({"CLOSED,false", "REFUND_PENDING,true", "REFUNDED,false", "PAID,error"})
    void repeatedCancellationOfClosedAppointmentChecksPaymentState(String payment, String result) {
        fixture();
        jdbcTemplate.update("UPDATE appointment_orders SET status='CANCELLED' WHERE id=11003");
        jdbcTemplate.update("UPDATE payment_orders SET status=? WHERE id=11004", payment);
        if ("error".equals(result)) {
            assertThatThrownBy(() -> lifecycle.cancelOwned(11001L, 11003L))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        } else {
            assertThat(lifecycle.cancelOwned(11001L, 11003L)).isEqualTo(Boolean.parseBoolean(result));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT held_capacity FROM schedules WHERE id=4", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"UNPAID,false", "PAYING,false", "PAID,true"})
    void cancellationAfterExpiryKeepsCancellationRefundReason(String payment, boolean refund) {
        fixture();
        jdbcTemplate.update("UPDATE appointment_orders SET payment_expires_at=? WHERE id=11003",
                LocalDateTime.now(appointmentClock).minusSeconds(1));
        jdbcTemplate.update("UPDATE payment_orders SET status=? WHERE id=11004", payment);
        assertThat(lifecycle.cancelOwned(11001L, 11003L)).isEqualTo(refund);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003", String.class))
                .isEqualTo("EXPIRED");
        if (refund) {
            assertThat(jdbcTemplate.queryForObject("SELECT reason FROM payment_refunds WHERE payment_order_id=11004", String.class))
                    .isEqualTo("APPOINTMENT_CANCELLED");
        }
    }

    @ParameterizedTest
    @CsvSource({"-1,CONFIRMED", "0,EXPIRED", "1,EXPIRED"})
    void expiryUsesPersistedPaidAtEvenWhenConfirmationIsDelayed(int offset, String expected) {
        fixture();
        LocalDateTime deadline = LocalDateTime.now(appointmentClock).minusSeconds(5).withNano(0);
        jdbcTemplate.update("UPDATE appointment_orders SET payment_expires_at=? WHERE id=11003", deadline);
        jdbcTemplate.update("UPDATE payment_orders SET paid_at=? WHERE id=11004", deadline.plusSeconds(offset));
        assertThat(lifecycle.expireIfDue(11003L)).isTrue();
        assertThat(lifecycle.expireIfDue(11003L)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003", String.class))
                .isEqualTo(expected);
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }

    @Test
    void confirmedCancellationAtExactTwentyFourHoursIsRejected() {
        fixture();
        jdbcTemplate.update("UPDATE appointment_orders SET status='CONFIRMED', appointment_date='2026-11-10', start_time='08:00:00' WHERE id=11003");
        assertThatThrownBy(() -> lifecycle.cancelOwned(11001L, 11003L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CANCELLATION_DEADLINE_REACHED));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=11004", Integer.class)).isZero();
    }

    @Test
    void newEventForConfirmedAppointmentDoesNotRequirePaymentLookup() {
        fixture();
        jdbcTemplate.update("UPDATE appointment_orders SET status='CONFIRMED' WHERE id=11003");
        jdbcTemplate.update("DELETE FROM payment_attempts WHERE payment_order_id=11004");
        jdbcTemplate.update("DELETE FROM payment_orders WHERE id=11004");
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                "00000000-0000-0000-0000-000000001106", 1, LocalDateTime.of(2098, 12, 1, 8, 0),
                11_004L, 11_003L, 11_005L, new BigDecimal("50.00"), "CNY", "MOCK",
                LocalDateTime.of(2098, 12, 1, 8, 0));
        assertThat(processor.process(event).name()).isEqualTo("ALREADY_CONFIRMED");
        assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4", Integer.class)).isEqualTo(1);
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

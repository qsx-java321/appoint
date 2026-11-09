package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.payment.application.PaymentLifecycle;
import com.qsx.appoint.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:lifecycle-interface;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.messaging.enabled=false", "appoint.expiration.enabled=false"})
class AppointmentLifecycleInterfaceTest {
    @Autowired private AppointmentLifecycle appointments;
    @Autowired private PaymentLifecycle payments;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transaction;

    @BeforeEach
    void prepare() {
        jdbcTemplate.update("DELETE FROM appointment_active_claims WHERE appointment_order_id=11003");
        jdbcTemplate.update("DELETE FROM payment_attempts WHERE payment_order_id=11004");
        jdbcTemplate.update("DELETE FROM payment_orders WHERE id=11004");
        jdbcTemplate.update("DELETE FROM appointment_orders WHERE id=11003");
        jdbcTemplate.update("DELETE FROM patients WHERE id=11002");
        jdbcTemplate.update("DELETE FROM users WHERE id=11001");
        fixture();
    }

    @Test
    void mandatoryRejectsLocksAndWritesWithoutCallerTransaction() {
        var appointment=appointments.find(11003L);
        assertThatThrownBy(() -> appointments.lock(11003L)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> appointments.confirm(appointment)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> appointments.closePending(appointment,AppointmentCloseReason.USER_CANCELLED,LocalDateTime.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> appointments.cancelConfirmed(appointment,LocalDateTime.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.lockByAppointment(11003L)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.closeUnpaid(payments.find(11004L))).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(state()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void findAndPreviousTransactionSnapshotsCannotAuthorizeWrites() {
        var readOnly=appointments.find(11003L);
        assertThatThrownBy(() -> transaction.execute(status -> appointments.confirm(readOnly))).isInstanceOf(BusinessException.class);
        var prior=transaction.execute(status -> appointments.lock(11003L));
        assertThatThrownBy(() -> transaction.execute(status -> appointments.confirm(prior))).isInstanceOf(BusinessException.class);
        jdbcTemplate.update("UPDATE payment_orders SET status='UNPAID' WHERE id=11004");
        var payment=payments.find(11004L);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> payments.closeUnpaid(payment))).isInstanceOf(BusinessException.class);
        assertThat(state()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void suspendedOuterContextCannotBeUsedInRequiresNewTransaction() {
        var inner=new TransactionTemplate(transaction.getTransactionManager());
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            var locked=appointments.lock(11003L);
            assertThatThrownBy(() -> inner.execute(innerStatus -> appointments.confirm(locked))).isInstanceOf(BusinessException.class);
            assertThat(appointments.confirm(locked).visitNumber()).isEqualTo(1);
        });
        assertThat(state()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmationParticipatesInOuterRollbackIncludingVisitNumber() {
        transaction.executeWithoutResult(status -> {
            assertThat(appointments.confirm(appointments.lock(11003L)).visitNumber()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(state()).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbcTemplate.queryForObject("SELECT held_capacity FROM schedules WHERE id=4",Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4",Integer.class)).isEqualTo(1);
    }

    @Test
    void failedClaimRemovalRollsBackPendingCloseAsOneOperation() {
        jdbcTemplate.update("DELETE FROM appointment_active_claims WHERE appointment_order_id=11003");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> appointments.closePending(
                appointments.lock(11003L),AppointmentCloseReason.PAYMENT_EXPIRED,LocalDateTime.now())))
                .isInstanceOf(BusinessException.class);
        assertThat(state()).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbcTemplate.queryForObject("SELECT held_capacity FROM schedules WHERE id=4",Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT available_capacity FROM schedules WHERE id=4",Integer.class)).isEqualTo(1);
    }

    @Test
    void failedClaimRemovalRollsBackConfirmedCancellation() {
        transaction.executeWithoutResult(status -> appointments.confirm(appointments.lock(11003L)));
        jdbcTemplate.update("DELETE FROM appointment_active_claims WHERE appointment_order_id=11003");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> appointments.cancelConfirmed(
                appointments.lock(11003L),LocalDateTime.now()))).isInstanceOf(BusinessException.class);
        assertThat(state()).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject("SELECT confirmed_capacity FROM schedules WHERE id=4",Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT next_visit_number FROM schedules WHERE id=4",Integer.class)).isEqualTo(2);
    }

    @Test
    void paymentCloseParticipatesInTheSameOuterRollback() {
        jdbcTemplate.update("UPDATE payment_orders SET status='UNPAID' WHERE id=11004");
        transaction.executeWithoutResult(status -> {
            payments.closeUnpaid(payments.lockByAppointment(11003L));
            status.setRollbackOnly();
        });
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment_orders WHERE id=11004",String.class)).isEqualTo("UNPAID");
    }

    @Test
    void missingAndHistoricalNullSessionKeepExistingErrors() {
        transaction.executeWithoutResult(status -> assertThat(appointments.lock(-1L)).isNull());
        jdbcTemplate.update("UPDATE schedules SET session_type=NULL WHERE id=4");
        assertThatThrownBy(() -> transaction.execute(status -> appointments.lock(11003L)))
                .isInstanceOfSatisfying(BusinessException.class,exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(com.qsx.appoint.common.ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String state() { return jdbcTemplate.queryForObject("SELECT status FROM appointment_orders WHERE id=11003",String.class); }

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
    }}

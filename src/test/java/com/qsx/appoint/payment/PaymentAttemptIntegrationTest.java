package com.qsx.appoint.payment;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.payment.application.PaymentAttemptService;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;
import com.qsx.appoint.payment.entity.PaymentAttempt;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentAttemptMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.qsx.appoint.support.CommittedFixtureScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spring/H2验证支付尝试的分段事务及渠道调用不占事务；每例清理已提交数据。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-attempt;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.expiration.enabled=false"
})
@ActiveProfiles("test")
@Import(PaymentAttemptIntegrationTest.GatewayConfiguration.class)
class PaymentAttemptIntegrationTest {
    private CommittedFixtureScope fixtureScope;

    @BeforeEach
    void trackCommittedFixture() { fixtureScope = new CommittedFixtureScope(jdbcTemplate); }

    @AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    private static final AtomicLong IDS = new AtomicLong(8_000_000);
    @Autowired private PaymentAttemptService service;
    @Autowired private PaymentOrderMapper paymentOrderMapper;
    @Autowired private PaymentAttemptMapper paymentAttemptMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FailingGateway failingGateway;

    @Test
    void successfulStartCreatesOneProcessingAttemptAndBlocksASecondActiveAttempt() {
        Fixture fixture = fixture(false, LocalDateTime.of(2099, 1, 1, 8, 15));
        PaymentAttemptResponse response = service.startAttempt(fixture.userId(), fixture.paymentId(), PaymentChannel.MOCK);

        assertThat(response.paymentStatus()).isEqualTo("PAYING");
        assertThat(response.attemptStatus()).isEqualTo("PROCESSING");
        assertThat(response.checkoutUrl()).contains("/api/v1/mock-pay/attempts/");
        assertThatThrownBy(() -> service.startAttempt(fixture.userId(), fixture.paymentId(), PaymentChannel.MOCK))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_ERROR));
        assertThat(paymentAttemptMapper.selectById(response.paymentAttemptId()).getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void gatewayFailureRunsOutsideTransactionAndRestoresPaymentForRetry() {
        Fixture fixture = fixture(false, LocalDateTime.of(2099, 1, 1, 8, 15));
        assertThatThrownBy(() -> service.startAttempt(fixture.userId(), fixture.paymentId(), PaymentChannel.WECHAT_PAY))
                .isInstanceOf(IllegalStateException.class);

        assertThat(failingGateway.calledInsideTransaction()).isFalse();
        PaymentOrder payment = paymentOrderMapper.selectById(fixture.paymentId());
        assertThat(payment.getStatus()).isEqualTo("UNPAID");
        assertThat(paymentAttemptMapper.selectByPaymentOrderIdForUpdate(fixture.paymentId()))
                .extracting(PaymentAttempt::getStatus).containsExactly("FAILED");
        assertThatThrownBy(() -> service.startAttempt(fixture.userId(), fixture.paymentId(), PaymentChannel.WECHAT_PAY))
                .isInstanceOf(IllegalStateException.class);
        assertThat(paymentAttemptMapper.selectByPaymentOrderIdForUpdate(fixture.paymentId())).hasSize(2);
    }

    @Test
    void ownerDeadlineAndLegacyRulesAreEnforcedBeforeGatewayCall() {
        Fixture normal = fixture(false, LocalDateTime.of(2099, 1, 1, 8, 15));
        assertThatThrownBy(() -> service.startAttempt(normal.userId() + 1, normal.paymentId(), PaymentChannel.MOCK))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        Fixture expired = fixture(false, LocalDateTime.of(2000, 1, 1, 8, 15));
        assertThatThrownBy(() -> service.startAttempt(expired.userId(), expired.paymentId(), PaymentChannel.MOCK))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_EXPIRED));
        Fixture legacy = fixture(true, LocalDateTime.of(2099, 1, 1, 8, 15));
        assertThatThrownBy(() -> service.startAttempt(legacy.userId(), legacy.paymentId(), PaymentChannel.MOCK))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_STATE_ERROR));
    }

    private Fixture fixture(boolean legacy, LocalDateTime expiresAt) {
        long userId = IDS.incrementAndGet();
        long patientId = IDS.incrementAndGet();
        long appointmentId = IDS.incrementAndGet();
        long paymentId = IDS.incrementAndGet();
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash) VALUES (?,?,?)",
                userId, "attempt-" + userId + "@example.com", "hash");
        jdbcTemplate.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email)
                VALUES (?,?,?,'RESIDENT_ID_CARD',?,?,?)
                """, patientId, userId, "测试", "110105199001010010" + userId, "13800000000", "p@example.com");
        jdbcTemplate.update("""
                INSERT INTO appointment_orders
                  (id,appointment_no,user_id,schedule_id,patient_id,patient_name,patient_document_type,
                   patient_document_no,patient_contact_phone,patient_contact_email,doctor_id,doctor_no,
                   doctor_name,doctor_title,department_id,department_name,hospital_id,hospital_name,
                   hospital_address,appointment_date,start_time,end_time,status,payment_expires_at,
                   fee_amount,fee_currency)
                VALUES (?,?,?,1,?,'测试','RESIDENT_ID_CARD',?,'13800000000','p@example.com',1,'DOC000001',
                        '张医生','主任医师',1,'内科',1,'示例市第一人民医院','示例市健康路 1 号',
                        '2099-01-01','09:00:00','09:30:00','PENDING_PAYMENT',?,50.00,'CNY')
                """, appointmentId, "A" + appointmentId, userId, patientId,
                "110105199001010010" + userId, expiresAt);
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (id,payment_no,appointment_order_id,user_id,status,amount,currency,expires_at,legacy)
                VALUES (?,?,?,?,'UNPAID',50.00,'CNY',?,?)
                """, paymentId, "P" + paymentId, appointmentId, userId, expiresAt, legacy);
        return new Fixture(userId, paymentId);
    }

    record Fixture(Long userId, Long paymentId) {}

    @TestConfiguration
    static class GatewayConfiguration {
        @Bean
        FailingGateway failingGateway() {
            return new FailingGateway();
        }
    }

    static class FailingGateway implements PaymentGateway {
        private final AtomicBoolean calledInsideTransaction = new AtomicBoolean();

        @Override public PaymentChannel channel() { return PaymentChannel.WECHAT_PAY; }
        @Override public GatewayPaymentSession createPayment(GatewayPaymentRequest request) {
            calledInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            throw new IllegalStateException("simulated gateway outage");
        }
        @Override public void closePayment(GatewayCloseRequest request) { throw new UnsupportedOperationException(); }
        @Override public GatewayRefundSubmission refund(GatewayRefundRequest request) { throw new UnsupportedOperationException(); }
        @Override public GatewayPaymentSnapshot queryPayment(String providerOrderNo) { throw new UnsupportedOperationException(); }
        @Override public VerifiedPaymentNotification verifyNotification(RawPaymentNotification notification) { throw new UnsupportedOperationException(); }
        boolean calledInsideTransaction() { return calledInsideTransaction.get(); }
    }
}

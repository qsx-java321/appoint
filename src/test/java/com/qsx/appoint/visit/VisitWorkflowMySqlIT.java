package com.qsx.appoint.visit;

import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.catalog.service.DoctorAccountService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.support.FormalPaymentTestSupport;
import com.qsx.appoint.support.MutableAppointmentClock;
import com.qsx.appoint.support.AppointmentClockTestConfiguration;
import com.qsx.appoint.visit.application.VisitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.qsx.appoint.support.CommittedFixtureScope;
import com.qsx.appoint.support.TestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.*;

/** 类独占MySQL验证并发绑定、签到、接诊只形成唯一身份和记录；提交数据按例清理。 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@RegressionTest
@ActiveProfiles("regression")
@Import({FormalPaymentTestSupport.class, AppointmentClockTestConfiguration.class})
class VisitWorkflowMySqlIT {
    private CommittedFixtureScope fixtureScope;

    @BeforeEach
    void trackCommittedFixture() { fixtureScope = new CommittedFixtureScope(jdbc); }

    @AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-lock-wait-timeout=10").withDatabaseName("appoint").withUsername("appoint").withPassword("appoint-test");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
    @Autowired DoctorAccountService doctors;
    @Autowired VisitService visits;
    @Autowired BookingCoordinator bookings;
    @Autowired AppointmentService appointments;
    @Autowired FormalPaymentTestSupport payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableAppointmentClock clock;

    @BeforeEach
    void resetClock() { clock.set("2026-11-12T08:00:00"); }

    @Test
    void concurrentBindingCheckInStartAndCompletionKeepOneIdentityAndOneVisit() throws Exception {
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(90001,'owner-mysql@example.com','hash'),(90002,'doctor-mysql@example.com','hash')");
        var binding = race(() -> doctors.bindAccount(1L, 90002L), () -> doctors.bindAccount(2L, 90002L));
        assertThat(binding.stream().filter(BusinessException.class::isInstance).map(BusinessException.class::cast)
                .map(BusinessException::getErrorCode)).containsExactly(ErrorCode.DOCTOR_ACCOUNT_ALREADY_BOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles WHERE user_id=90002", Integer.class)).isEqualTo(1);
        Long doctorId = doctors.requireDoctorId(90002L);
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) VALUES(90003,90001,'MySQL就诊人','RESIDENT_ID_CARD','110105199001010010','13800138000','p@example.com',TRUE)");
        clock.set("2026-11-12T08:00:00");
        var request = new CreateAppointmentRequest();
        request.setDoctorId(doctorId); request.setPatientId(90003L);
        request.setAppointmentDate(LocalDate.of(2026,11,13)); request.setSessionType("MORNING_FIRST");
        var created = (CreateAppointmentResponse) bookings.create(90001L, UUID.randomUUID().toString(), request);
        payments.pay(90001L, created.getPaymentOrderId());
        Long id = created.getAppointmentOrderId();
        var facts = jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=?", id);
        var inventory = jdbc.queryForMap("SELECT * FROM schedules WHERE id=?", facts.get("schedule_id"));
        var paymentFacts = jdbc.queryForMap("SELECT * FROM payment_orders WHERE id=?", created.getPaymentOrderId());
        var claimFacts = jdbc.queryForMap("SELECT * FROM appointment_active_claims WHERE appointment_order_id=?", id);
        clock.set("2026-11-13T07:29:59");
        assertThatThrownBy(() -> visits.checkIn(90001L,id)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CHECK_IN_NOT_OPEN));
        clock.set("2026-11-13T10:00:00");
        assertThatThrownBy(() -> visits.checkIn(90001L,id)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CHECK_IN_CLOSED));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records WHERE appointment_order_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=?",id)).isEqualTo(facts);
        assertThat(jdbc.queryForMap("SELECT * FROM schedules WHERE id=?",facts.get("schedule_id"))).isEqualTo(inventory);
        assertThat(jdbc.queryForMap("SELECT * FROM payment_orders WHERE id=?", created.getPaymentOrderId())).isEqualTo(paymentFacts);
        assertThat(jdbc.queryForMap("SELECT * FROM appointment_active_claims WHERE appointment_order_id=?", id)).isEqualTo(claimFacts);
        clock.set("2026-11-13T09:59:59");
        assertOneConflict(race(() -> { visits.checkIn(90001L,id); return "OK"; }, () -> { visits.checkIn(90001L,id); return "OK"; }));
        var checkedIn = appointments.getOwned(90001L,id).getVisit().checkedInAt();
        assertThat(checkedIn).isEqualTo(java.time.LocalDateTime.parse("2026-11-13T09:59:59"));
        clock.set("2026-11-13T10:10:00");
        assertOneConflict(race(() -> { visits.startVisit(90002L,id); return "OK"; }, () -> { visits.startVisit(90002L,id); return "OK"; }));
        var started = appointments.getOwned(90001L,id).getVisit().startedAt();
        clock.set("2026-11-14T08:00:00");
        assertOneConflict(race(() -> { visits.completeVisit(90002L,id); return "OK"; }, () -> { visits.completeVisit(90002L,id); return "OK"; }));
        var completed = appointments.getOwned(90001L,id);
        assertThat(completed.getVisit().status()).isEqualTo("COMPLETED");
        assertThat(completed.getVisit().checkedInAt()).isEqualTo(checkedIn);
        assertThat(completed.getVisit().startedAt()).isEqualTo(started);
        assertThat(jdbc.queryForMap("SELECT * FROM payment_orders WHERE id=?", created.getPaymentOrderId())).isEqualTo(paymentFacts);
        assertThat(jdbc.queryForMap("SELECT * FROM appointment_active_claims WHERE appointment_order_id=?", id)).isEqualTo(claimFacts);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records WHERE appointment_order_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=?",id)).isEqualTo(facts);
        assertThat(jdbc.queryForMap("SELECT * FROM schedules WHERE id=?",facts.get("schedule_id"))).isEqualTo(inventory);
    }
    private void assertOneConflict(List<Object> results) {
        assertThat(results.stream().filter("OK"::equals).count()).isEqualTo(1);
        assertThat(results.stream().filter(BusinessException.class::isInstance).map(BusinessException.class::cast)
                .map(BusinessException::getErrorCode)).containsExactly(ErrorCode.VISIT_STATE_ERROR);
    }
    private List<Object> race(Callable<?> left, Callable<?> right) throws Exception {
        var gate = new CountDownLatch(1);
        try (var executor = new TestExecutor(2)) {
            Callable<Object> a = () -> { if (!gate.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("VisitWorkflowMySqlIT: gate屏障超时"); try { return left.call(); } catch (BusinessException error) { return error; } };
            Callable<Object> b = () -> { if (!gate.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("VisitWorkflowMySqlIT: gate屏障超时"); try { return right.call(); } catch (BusinessException error) { return error; } };
            var first = executor.submit(a); var second = executor.submit(b); gate.countDown();
            return List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
        }
    }
}

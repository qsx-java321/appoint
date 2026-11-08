package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.ReplayedAppointmentResponse;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:midnight-booking;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.expiration.enabled=false", "appoint.messaging.enabled=false", "appoint.outbox.enabled=false"
})
@Import(BookingMidnightIntegrationTest.ClockConfig.class)
class BookingMidnightIntegrationTest {
    @Autowired private BookingCoordinator coordinator;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MidnightClock clock;

    @Test
    void midnightDuringCreationRollsBackAndPreviouslyCompletedKeyCanReplay() {
        jdbc.update("INSERT INTO users (id,email,password_hash) VALUES (99001,'midnight-test@example.com','hash')");
        jdbc.update("""
                INSERT INTO patients (id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled)
                VALUES (99002,99001,'测试','RESIDENT_ID_CARD','110105199001010010','13800000000','p@example.com',TRUE)
                """);
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setDoctorId(1L);
        request.setPatientId(99002L);
        request.setAppointmentDate(LocalDate.of(2026, 11, 6));
        request.setSessionType("MORNING_FIRST");
        String key = UUID.randomUUID().toString();
        clock.crossAtFinalAcceptance();

        assertThatThrownBy(() -> coordinator.create(99001L, key, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SCHEDULE_CUTOFF_REACHED));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules WHERE doctor_id=1 AND appointment_date='2026-11-06'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_orders WHERE user_id=99001", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_orders WHERE user_id=99001", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_active_claims WHERE patient_id=99002", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_create_requests WHERE user_id=99001", Integer.class)).isZero();

        clock.beforeMidnight();
        coordinator.create(99001L, key, request);
        clock.afterMidnight();
        assertThat(coordinator.create(99001L, key, request)).isInstanceOf(ReplayedAppointmentResponse.class);
    }

    static class ClockConfig {
        @Bean @Primary MidnightClock midnightClock() { return new MidnightClock(); }
    }

    static class MidnightClock extends Clock {
        private static final Instant BEFORE = Instant.parse("2026-11-05T15:59:59Z");
        private static final Instant AFTER = Instant.parse("2026-11-05T16:00:00Z");
        private final AtomicInteger calls = new AtomicInteger();
        private volatile int crossAfter = Integer.MAX_VALUE;
        void crossAtFinalAcceptance() { calls.set(0); crossAfter = 4; }
        void beforeMidnight() { calls.set(0); crossAfter = Integer.MAX_VALUE; }
        void afterMidnight() { calls.set(0); crossAfter = 0; }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return calls.incrementAndGet() <= crossAfter ? BEFORE : AFTER; }
    }
}

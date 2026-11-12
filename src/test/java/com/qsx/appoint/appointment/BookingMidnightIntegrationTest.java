package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.dto.ReplayedAppointmentResponse;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.support.MutableAppointmentClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:midnight-booking;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.expiration.enabled=false", "appoint.messaging.enabled=false", "appoint.outbox.enabled=false"
})
@Import(com.qsx.appoint.support.AppointmentClockTestConfiguration.class)
class BookingMidnightIntegrationTest {
    @Autowired private org.springframework.jdbc.core.JdbcTemplate fixtureJdbc;
    private com.qsx.appoint.support.CommittedFixtureScope fixtureScope;

    @org.junit.jupiter.api.BeforeEach
    void trackCommittedFixture() { fixtureScope = new com.qsx.appoint.support.CommittedFixtureScope(fixtureJdbc); }

    @org.junit.jupiter.api.AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Autowired private BookingCoordinator coordinator;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableAppointmentClock clock;

    @org.junit.jupiter.api.BeforeEach
    void resetClock() { clock.set("2026-11-05T23:59:59"); }

    @Test
    void previouslyCompletedKeyReplaysEvenAfterOriginalDateLeavesCreationWindow() {
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
        clock.set("2026-11-05T23:59:59");
        var created = (CreateAppointmentResponse) coordinator.create(99001L, key, request);
        clock.set("2026-11-08T00:00:00");
        var replay = (ReplayedAppointmentResponse) coordinator.create(99001L, key, request);
        assertThat(replay.getAppointmentOrderId()).isEqualTo(created.getAppointmentOrderId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_orders WHERE user_id=99001", Integer.class)).isEqualTo(1);
    }

}

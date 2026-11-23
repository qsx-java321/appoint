package com.qsx.appoint.notification;

import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.notification.mapper.NotificationOutboxEventMapper;
import com.qsx.appoint.security.JwtTokenProvider;
import com.qsx.appoint.support.AppointmentClockTestConfiguration;
import com.qsx.appoint.support.CommittedFixtureScope;
import com.qsx.appoint.support.FormalPaymentTestSupport;
import com.qsx.appoint.support.MutableAppointmentClock;
import com.qsx.appoint.support.RegressionTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通知事件写入失败时，业务状态、库存和签到/接诊记录必须随外层事务整体回滚。 */
@RegressionTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-rollback;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("regression")
@Import({FormalPaymentTestSupport.class, AppointmentClockTestConfiguration.class})
class NotificationTransactionRollbackTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired BookingCoordinator bookings;
    @Autowired FormalPaymentTestSupport payments;
    @Autowired MutableAppointmentClock clock;
    @MockitoBean NotificationOutboxEventMapper outbox;

    String owner, doctor, admin;
    CommittedFixtureScope fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new CommittedFixtureScope(jdbc);
        clock.set("2026-11-12T08:00:00");
        for (long id = 86001; id <= 86003; id++) {
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,'unused')", id, id + "@rollback.example.com");
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='USER'", id);
        }
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT 86002,id FROM roles WHERE code='DOCTOR'");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT 86003,id FROM roles WHERE code='ADMIN'");
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) "
                + "VALUES(86011,86001,'回滚就诊人','RESIDENT_ID_CARD','110105199001010029','13800138000','p@example.com',TRUE)");
        owner = token(86001L, "USER");
        doctor = token(86002L, "USER", "DOCTOR");
        admin = token(86003L, "ADMIN");
        mvc.perform(put("/api/v1/admin/doctors/1/account").header("Authorization", admin)
                        .contentType("application/json").content("{\"userId\":\"86002\"}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void cleanup() {
        fixture.close();
    }

    @Test
    void paymentEventFailureRollsBackPaymentAppointmentAndInventory() {
        long id = create("MORNING_FIRST", false).getAppointmentOrderId();
        failOutbox();

        assertThatThrownBy(() -> payments.pay(86001L, id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessage("outbox down");

        assertThat(state("appointment_orders", id)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_orders WHERE appointment_order_id=?",
                String.class, id)).isEqualTo("UNPAID");
        assertThat(jdbc.queryForObject("SELECT held_capacity FROM schedules WHERE id="
                + "(SELECT schedule_id FROM appointment_orders WHERE id=?)", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void checkInEventFailureRollsBackVisitRecord() throws Exception {
        long id = paidAppointment();
        failOutbox();
        clock.set("2026-11-13T07:35:00");

        mvc.perform(post("/api/v1/appointments/" + id + "/check-in").header("Authorization", owner))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records WHERE appointment_order_id=?",
                Integer.class, id)).isZero();
        assertThat(state("appointment_orders", id)).isEqualTo("CONFIRMED");
    }

    @Test
    void startEventFailureKeepsVisitWaiting() throws Exception {
        long id = paidAppointment();
        clock.set("2026-11-13T07:35:00");
        checkIn(id);
        failOutbox();
        clock.set("2026-11-13T08:15:00");

        mvc.perform(post("/api/v1/doctor/appointments/" + id + "/start-visit").header("Authorization", doctor))
                .andExpect(status().isInternalServerError());

        var record = jdbc.queryForMap("SELECT status, started_at FROM visit_records WHERE appointment_order_id=?", id);
        assertThat(record.get("status")).isEqualTo("WAITING");
        assertThat(record.get("started_at")).isNull();
    }

    @Test
    void completeEventFailureKeepsVisitInProgress() throws Exception {
        long id = paidAppointment();
        clock.set("2026-11-13T07:35:00");
        checkIn(id);
        clock.set("2026-11-13T08:15:00");
        mvc.perform(post("/api/v1/doctor/appointments/" + id + "/start-visit").header("Authorization", doctor))
                .andExpect(status().isOk());
        failOutbox();
        clock.set("2026-11-13T08:28:00");

        mvc.perform(post("/api/v1/doctor/appointments/" + id + "/complete-visit").header("Authorization", doctor))
                .andExpect(status().isInternalServerError());

        var record = jdbc.queryForMap("SELECT status, completed_at FROM visit_records WHERE appointment_order_id=?", id);
        assertThat(record.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(record.get("completed_at")).isNull();
    }

    private long paidAppointment() {
        long id = create("MORNING_FIRST", false).getAppointmentOrderId();
        payments.pay(86001L, id);
        return id;
    }

    private void checkIn(long id) throws Exception {
        mvc.perform(post("/api/v1/appointments/" + id + "/check-in").header("Authorization", owner))
                .andExpect(status().isOk());
    }

    private void failOutbox() {
        doThrow(new DataIntegrityViolationException("outbox down"))
                .when(outbox).insertIgnore(any());
    }

    private CreateAppointmentResponse create(String session, boolean pay) {
        var request = new CreateAppointmentRequest();
        request.setDoctorId(1L);
        request.setPatientId(86011L);
        request.setAppointmentDate(LocalDate.parse("2026-11-13"));
        request.setSessionType(session);
        var response = (CreateAppointmentResponse) bookings.create(
                86001L, bookings.issueToken(86001L).token(), request);
        if (pay) payments.pay(86001L, response.getAppointmentOrderId());
        return response;
    }

    private String state(String table, long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=?", String.class, id);
    }

    private String token(Long id, String... roles) {
        return "Bearer " + tokens.createToken(id, id + "@rollback.example.com", List.of(roles));
    }
}

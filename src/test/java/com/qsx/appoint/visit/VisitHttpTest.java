package com.qsx.appoint.visit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.security.JwtTokenProvider;
import com.qsx.appoint.support.FormalPaymentTestSupport;
import com.qsx.appoint.support.MutableAppointmentClock;
import com.qsx.appoint.support.AppointmentClockTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import com.qsx.appoint.catalog.model.AppointmentSession;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spring/H2与MockMvc验证绑定、签到和接诊全流程及快照不变；每例重置时钟并回滚。 */
@RegressionTest(properties = "spring.datasource.url=jdbc:h2:mem:visit-http;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("regression")
@Import({FormalPaymentTestSupport.class, AppointmentClockTestConfiguration.class})
@Transactional
class VisitHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired BookingCoordinator bookings;
    @Autowired com.qsx.appoint.coordination.application.BookingLifecycleCoordinator lifecycle;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired FormalPaymentTestSupport payments;
    @Autowired MutableAppointmentClock clock;
    String owner, doctor, otherDoctor, admin, stranger;

    @BeforeEach
    void setup() {
        clock.set("2026-11-12T08:00:00");
        for (long id = 81001; id <= 81005; id++) {
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(?,?, 'unused')", id, id + "@visit.example.com");
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='USER'", id);
        }
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) VALUES(81011,81001,'就诊人甲','RESIDENT_ID_CARD','110105199001010010','13800138000','p@example.com',TRUE)");
        owner = token(81001L, "USER"); doctor = token(81002L, "USER", "DOCTOR");
        otherDoctor = token(81003L, "DOCTOR"); admin = token(81004L, "ADMIN"); stranger = token(81005L, "USER");
    }

    @Test
    void userAndDoctorShareProgressAndPreserveBookingFactsThroughTheWholeVisit() throws Exception {
        bind(1, 81002, admin, 200);
        bind(2, 81003, admin, 200);
        var appointment = create("2026-11-13", "MORNING_FIRST", true);
        long id = appointment.getAppointmentOrderId();
        var facts = jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=?", id);
        var inventory = jdbc.queryForMap("SELECT * FROM schedules WHERE id=(SELECT schedule_id FROM appointment_orders WHERE id=?)", id);
        var detail = getUser(id);
        assertThat(detail.path("visit").path("status").asText()).isEqualTo("NOT_CHECKED_IN");
        assertThat(detail.path("visit").path("canCheckIn").asBoolean()).isFalse();
        rejectCheckIn(id, owner, 409, 4503);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records", Integer.class)).isZero();
        clock.set("2026-11-13T00:00:00"); // 上海零点，仍未到07:30开放时间。
        assertThat(getUser(id).path("visit").path("canCheckIn").asBoolean()).isFalse();
        rejectCheckIn(id, owner, 409, 4503);
        clock.set("2026-11-13T07:30:00");
        assertThat(getUser(id).path("visit").path("canCheckIn").asBoolean()).isTrue();
        assertRow(id, "NOT_CHECKED_IN", false, false);
        var waiting = action("/appointments/" + id + "/check-in", owner, 200).path("visit");
        assertThat(waiting.path("checkedInAt").asText()).isEqualTo("2026-11-13T07:30:00");
        assertRow(id, "WAITING", true, false);
        action("/appointments/" + id + "/check-in", owner, 409);
        clock.set("2026-11-13T08:15:00");
        var started = action("/doctor/appointments/" + id + "/start-visit", doctor, 200).path("visit");
        assertThat(started.path("startedAt").asText()).isEqualTo("2026-11-13T08:15:00");
        assertRow(id, "IN_PROGRESS", false, true);
        action("/doctor/appointments/" + id + "/start-visit", doctor, 409);
        clock.set("2026-11-13T08:28:00");
        var completed = action("/doctor/appointments/" + id + "/complete-visit", doctor, 200).path("visit");
        assertThat(completed.path("completedAt").asText()).isEqualTo("2026-11-13T08:28:00");
        assertRow(id, "COMPLETED", false, false);
        action("/doctor/appointments/" + id + "/complete-visit", doctor, 409);
        var userVisit = getUser(id).path("visit");
        for (String field : List.of("status", "checkedInAt", "startedAt", "completedAt")) {
            assertThat(userVisit.get(field)).isEqualTo(completed.get(field));
        }
        var sessions = getData("/doctor/sessions", doctor);
        assertThat(sessions).hasSize(4);
        assertThat(sessions.get(0).path("confirmedCount").asInt()).isEqualTo(1);
        assertThat(sessions.get(0).path("completedCount").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=?", id)).isEqualTo(facts);
        assertThat(jdbc.queryForMap("SELECT * FROM schedules WHERE id=?", facts.get("schedule_id"))).isEqualTo(inventory);
        assertThat(getUser(id).path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_active_claims WHERE appointment_order_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void sameDayBookingCanBePaidCheckedInAndCompletedWithoutChangingInventory() throws Exception {
        bind(1, 81002, admin, 200);
        var appointment = create("2026-11-12", "AFTERNOON_FIRST", true);
        long id = appointment.getAppointmentOrderId();
        var facts = jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=?", id);
        var inventory = jdbc.queryForMap("SELECT * FROM schedules WHERE id=?", facts.get("schedule_id"));
        assertThat(getUser(id).path("visit").path("canCheckIn").asBoolean()).isFalse();
        rejectCheckIn(id, owner, 409, 4503);
        clock.set("2026-11-12T13:30:00");
        assertThat(getUser(id).path("visit").path("canCheckIn").asBoolean()).isTrue();
        action("/appointments/" + id + "/check-in", owner, 200);
        action("/doctor/appointments/" + id + "/start-visit", doctor, 200);
        action("/doctor/appointments/" + id + "/complete-visit", doctor, 200);
        assertThat(getUser(id).path("visit").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(getData("/doctor/appointments/" + id, doctor).path("visit").path("status").asText())
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForMap("SELECT * FROM appointment_orders WHERE id=?", id)).isEqualTo(facts);
        assertThat(jdbc.queryForMap("SELECT * FROM schedules WHERE id=?", facts.get("schedule_id"))).isEqualTo(inventory);
    }

    @Test
    void bindingPreservesRolesRequiresReloginAndRejectsRebinding() throws Exception {
        String oldToken = token(81002L, "USER");
        bind(1, 81002, owner, 403);
        bind(1, 999999, admin, 404);
        assertThat(jdbc.queryForObject("SELECT user_id FROM doctors WHERE id=1", Long.class)).isNull();
        bind(1, 81002, admin, 200);
        bind(1, 81002, admin, 409);
        bind(2, 81002, admin, 409);
        assertThat(jdbc.queryForObject("SELECT user_id FROM doctors WHERE id=2", Long.class)).isNull();
        mvc.perform(get("/api/v1/doctor/sessions").header("Authorization", oldToken)).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET password_hash=? WHERE id=81002", passwords.encode("password123"));
        var login = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"81002@visit.example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        var roles = tokens.parseClaims(login.path("accessToken").asText()).get("roles", List.class);
        assertThat(roles).containsExactlyInAnyOrder("USER", "DOCTOR");
        assertThat(getData("/doctor/sessions", "Bearer " + login.path("accessToken").asText())).hasSize(4);
    }

    @Test
    void permissionsDatesAndStateEligibilityAreEnforcedAtWriteEntrypoints() throws Exception {
        bind(1, 81002, admin, 200);
        bind(2, 81003, admin, 200);
        var confirmed = create("2026-11-13", "MORNING_FIRST", true);
        long id = confirmed.getAppointmentOrderId();
        var pending = create("2026-11-13", "MORNING_SECOND", false);
        action("/appointments/" + id + "/check-in", stranger, 403);
        action("/appointments/999999/check-in", owner, 404);
        action("/doctor/appointments/" + id + "/start-visit", owner, 403);
        action("/doctor/appointments/" + id + "/start-visit", otherDoctor, 403);
        action("/doctor/appointments/" + id + "/start-visit", doctor, 409);
        action("/doctor/appointments/" + id + "/complete-visit", doctor, 409);
        mvc.perform(get("/api/v1/doctor/appointments/" + id).header("Authorization", otherDoctor)).andExpect(status().isNotFound());
        clock.set("2026-11-13T08:00:00");
        action("/appointments/" + pending.getAppointmentOrderId() + "/check-in", owner, 409);
        action("/doctor/appointments/" + pending.getAppointmentOrderId() + "/start-visit", doctor, 409);
        clock.set("2026-11-14T00:00:00");
        action("/appointments/" + id + "/check-in", owner, 409);
        assertThat(getUser(id).path("visit").path("canCheckIn").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records", Integer.class)).isZero();
    }

    @Test
    void allAppointmentsUseStableVisitTimeOrderingIncludingCancelledAndExpired() throws Exception {
        var later = create("2026-11-14", "MORNING_FIRST", false);
        var early = create("2026-11-12", "AFTERNOON_SECOND", false);
        var closed = create("2026-11-13", "MORNING_FIRST", false);
        lifecycle.cancelOwned(81001L, closed.getAppointmentOrderId());
        var tie = create("2026-11-13", "MORNING_FIRST", false);
        clock.set("2026-11-12T08:16:00");
        lifecycle.expireIfDue(later.getAppointmentOrderId());
        var desc = getData("/appointments/my", owner);
        var asc = getData("/appointments/my?order=ASC", owner);
        assertThat(ids(desc)).containsExactly(later.getAppointmentOrderId(), tie.getAppointmentOrderId(),
                closed.getAppointmentOrderId(), early.getAppointmentOrderId());
        assertThat(ids(asc)).containsExactly(early.getAppointmentOrderId(), closed.getAppointmentOrderId(),
                tie.getAppointmentOrderId(), later.getAppointmentOrderId());
        assertThat(desc.get(0).path("status").asText()).isEqualTo("EXPIRED");
        assertThat(desc.get(2).path("status").asText()).isEqualTo("CANCELLED");
        for (var row : desc) {
            assertThat(row.path("visit").isNull()).isTrue();
            assertThat(row.get("serverTime")).isEqualTo(desc.get(0).get("serverTime"));
        }
    }

    @Test
    void emptyFourSessionsAndInvalidParametersDoNotCreateBusinessRecords() throws Exception {
        bind(1, 81002, admin, 200);
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM schedules", Integer.class);
        var sessions = getData("/doctor/sessions", doctor);
        assertThat(sessions).hasSize(4);
        assertThat(sessions.get(0).path("appointmentDate").asText()).isEqualTo("2026-11-12");
        assertThat(sessions.get(3).path("sessionType").asText()).isEqualTo("AFTERNOON_SECOND");
        for (var session : sessions) assertThat(session.path("confirmedCount").asInt()).isZero();
        assertThat(getData("/doctor/appointments?date=2026-11-12&sessionType=MORNING_FIRST", doctor)).isEmpty();
        for (String path : List.of("/doctor/appointments", "/doctor/appointments?date=2026-11-13",
                "/doctor/appointments?date=bad&sessionType=MORNING_FIRST",
                "/doctor/appointments?date=2026-11-13&sessionType=MORNING", "/appointments/my?order=BAD")) {
            mvc.perform(get("/api/v1" + path).header("Authorization", doctor)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1001));
        }
        mvc.perform(get("/api/v1/doctor/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/doctor/sessions").header("Authorization", otherDoctor)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records", Integer.class)).isZero();
    }

    @Test
    void doctorKeepsNumberOrderCanSkipUnsignedPatientsAndHasNoCrossRowBusyRestriction() throws Exception {
        bind(1, 81002, admin, 200);
        var first = create("2026-11-13", "MORNING_FIRST", true);
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) VALUES(81012,81001,'就诊人乙','RESIDENT_ID_CARD','110105199001010029','13800138000','p@example.com',TRUE)");
        var second = createFor(81012L, "2026-11-13", "MORNING_FIRST", true);
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) VALUES(81013,81001,'就诊人丙','RESIDENT_ID_CARD','110105199001010037','13800138000','p@example.com',TRUE)");
        var third = createFor(81013L, "2026-11-13", "MORNING_FIRST", true);
        clock.set("2026-11-13T08:00:00");
        action("/appointments/" + second.getAppointmentOrderId() + "/check-in", owner, 200);
        action("/appointments/" + third.getAppointmentOrderId() + "/check-in", owner, 200);
        action("/doctor/appointments/" + third.getAppointmentOrderId() + "/start-visit", doctor, 200);
        action("/doctor/appointments/" + second.getAppointmentOrderId() + "/start-visit", doctor, 200);
        var rows = getData("/doctor/appointments?date=2026-11-13&sessionType=MORNING_FIRST", doctor);
        assertThat(rows.get(0).path("appointmentId").asLong()).isEqualTo(first.getAppointmentOrderId());
        assertThat(rows.get(1).path("appointmentId").asLong()).isEqualTo(second.getAppointmentOrderId());
        assertThat(rows.get(2).path("appointmentId").asLong()).isEqualTo(third.getAppointmentOrderId());
        assertThat(rows.get(0).path("visitNumber").asInt()).isEqualTo(1);
        assertThat(rows.get(2).path("visitNumber").asInt()).isEqualTo(3);
        var counts = getData("/doctor/sessions", doctor).get(0);
        assertThat(counts.path("confirmedCount").asInt()).isEqualTo(3);
        assertThat(counts.path("notCheckedInCount").asInt()).isEqualTo(1);
        assertThat(counts.path("inProgressCount").asInt()).isEqualTo(2);
    }

    static Stream<Arguments> checkInBoundaries() {
        LocalDate date = LocalDate.of(2026, 11, 13);
        return Stream.of(AppointmentSession.values()).flatMap(session -> {
            var opens = date.atTime(session.startTime()).minusMinutes(30);
            var closes = date.atTime(session.endTime());
            return Stream.of(Arguments.of(session.name(), opens.minusSeconds(1), 4503),
                    Arguments.of(session.name(), opens, 0),
                    Arguments.of(session.name(), date.atTime(session.startTime()).plusSeconds(1), 0),
                    Arguments.of(session.name(), closes.minusSeconds(1), 0),
                    Arguments.of(session.name(), closes, 4504),
                    Arguments.of(session.name(), closes.plusSeconds(1), 4504));
        });
    }

    @ParameterizedTest
    @MethodSource("checkInBoundaries")
    void allFourWindowsUseStrictBoundariesInQueriesAndWrites(String session, LocalDateTime now, int error) throws Exception {
        long id = create("2026-11-13", session, true).getAppointmentOrderId();
        clock.set(now.toString());
        var detail = getUser(id);
        var window = AppointmentSession.valueOf(session);
        assertThat(detail.path("visit").path("checkInOpensAt").asText())
                .isEqualTo("2026-11-13T" + window.startTime().minusMinutes(30) + ":00");
        assertThat(detail.path("visit").path("checkInClosesAt").asText())
                .isEqualTo("2026-11-13T" + window.endTime() + ":00");
        assertThat(detail.path("serverTime").asText()).isEqualTo(now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        assertThat(detail.path("visit").path("canCheckIn").asBoolean()).isEqualTo(error == 0);
        var row = getData("/appointments/my", owner).get(0);
        assertThat(row.path("visit")).isEqualTo(detail.path("visit"));
        if (error != 0) {
            rejectCheckIn(id, owner, 409, error);
        } else {
            var checked = action("/appointments/" + id + "/check-in", owner, 200).path("visit");
            assertThat(checked.path("checkedInAt")).isEqualTo(detail.path("serverTime"));
            assertThat(checked.path("checkInOpensAt")).isEqualTo(detail.path("visit").path("checkInOpensAt"));
            assertThat(checked.path("checkInClosesAt")).isEqualTo(detail.path("visit").path("checkInClosesAt"));
            assertThat(checked.path("canCheckIn").asBoolean()).isFalse();
        }
    }

    @Test
    void staleQueryDoesNotAuthorizeCheckInAtDeadlineOrNextDay() throws Exception {
        long id = create("2026-11-13", "MORNING_FIRST", true).getAppointmentOrderId();
        clock.set("2026-11-13T09:59:59");
        assertThat(getUser(id).path("visit").path("canCheckIn").asBoolean()).isTrue();
        clock.set("2026-11-13T10:00:00");
        rejectCheckIn(id, owner, 409, 4504);
        clock.set("2026-11-14T00:00:00");
        rejectCheckIn(id, owner, 409, 4504);
        assertThat(getUser(id).path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(getUser(id).path("visit").path("status").asText()).isEqualTo("NOT_CHECKED_IN");
    }

    @ParameterizedTest
    @CsvSource({"PENDING_PAYMENT", "CANCELLED", "EXPIRED"})
    void appointmentStateErrorsPrecedeBothWindowErrors(String state) throws Exception {
        long id = create("2026-11-13", "MORNING_FIRST", false).getAppointmentOrderId();
        if (state.equals("CANCELLED")) lifecycle.cancelOwned(81001L, id);
        if (state.equals("EXPIRED")) {
            clock.set("2026-11-12T08:16:00");
            lifecycle.expireIfDue(id);
        }
        assertThat(getUser(id).path("status").asText()).isEqualTo(state);
        assertThat(getUser(id).path("visit").isNull()).isTrue();
        rejectCheckIn(id, owner, 409, 4502);
        clock.set("2026-11-14T00:00:00");
        rejectCheckIn(id, owner, 409, 4502);
    }

    @Test
    void ownershipAndExistencePrecedeWindowErrorsWithoutSideEffects() throws Exception {
        long id = create("2026-11-13", "MORNING_FIRST", true).getAppointmentOrderId();
        rejectCheckIn(id, stranger, 403, 2002);
        rejectCheckIn(999999L, owner, 404, 4001);
        clock.set("2026-11-14T00:00:00");
        rejectCheckIn(id, stranger, 403, 2002);
        rejectCheckIn(999999L, owner, 404, 4001);
    }

    @Test
    void checkedInPatientCanStartAfterCloseAndCompleteNextDayWithStableWindowAndFirstTime() throws Exception {
        bind(1, 81002, admin, 200);
        long id = create("2026-11-13", "MORNING_FIRST", true).getAppointmentOrderId();
        clock.set("2026-11-13T09:59:00");
        var waiting = action("/appointments/" + id + "/check-in", owner, 200).path("visit");
        clock.set("2026-11-13T10:10:00");
        rejectCheckIn(id, owner, 409, 4502);
        var started = action("/doctor/appointments/" + id + "/start-visit", doctor, 200).path("visit");
        assertThat(started.path("startedAt").asText()).isEqualTo("2026-11-13T10:10:00");
        clock.set("2026-11-14T08:00:00");
        var completed = action("/doctor/appointments/" + id + "/complete-visit", doctor, 200).path("visit");
        var user = getUser(id).path("visit");
        for (String field : List.of("status", "checkedInAt", "startedAt", "completedAt")) {
            assertThat(user.get(field)).isEqualTo(completed.get(field));
        }
        assertThat(user.path("checkInOpensAt")).isEqualTo(waiting.path("checkInOpensAt"));
        assertThat(user.path("checkInClosesAt")).isEqualTo(waiting.path("checkInClosesAt"));
        assertThat(user.path("checkedInAt").asText()).isEqualTo("2026-11-13T09:59:00");
        assertThat(user.path("completedAt").asText()).isEqualTo("2026-11-14T08:00:00");
        assertThat(user.path("canCheckIn").asBoolean()).isFalse();
        rejectCheckIn(id, owner, 409, 4502);
    }

    private void rejectCheckIn(long id, String token, int status, int code) throws Exception {
        var before = bookingFacts();
        mvc.perform(post("/api/v1/appointments/" + id + "/check-in").header("Authorization", token))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
        assertThat(bookingFacts()).usingRecursiveComparison().isEqualTo(before);
    }

    // 比较完整业务行，失败不能改资金、库存、快照、号码、有效占用或既有签到时间。
    private Map<String, List<Map<String, Object>>> bookingFacts() {
        var facts = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("appointment_orders", "payment_orders", "payment_attempts", "payment_refunds",
                "schedules", "appointment_active_claims", "visit_records", "payment_outbox_events", "coordination_inbox_events")) {
            facts.put(table, jdbc.queryForList("SELECT * FROM " + table));
        }
        return facts;
    }

    private List<Long> ids(JsonNode rows) {
        var result = new java.util.ArrayList<Long>();
        rows.forEach(row -> result.add(row.path("id").asLong()));
        return result;
    }

    private void assertRow(long id, String status, boolean start, boolean complete) throws Exception {
        var rows = getData("/doctor/appointments?date=2026-11-13&sessionType=MORNING_FIRST", doctor);
        var row = rows.get(0);
        assertThat(row.path("appointmentId").asLong()).isEqualTo(id);
        assertThat(row.path("visitStatus").asText()).isEqualTo(status);
        assertThat(row.path("canStartVisit").asBoolean()).isEqualTo(start);
        assertThat(row.path("canCompleteVisit").asBoolean()).isEqualTo(complete);
        var detail = getData("/doctor/appointments/" + id, doctor);
        assertThat(detail.path("visit").path("status").asText()).isEqualTo(status);
        assertThat(detail.path("patient").path("documentNoMasked").asText()).contains("*");
        assertThat(detail.path("patient").has("contactPhone")).isFalse();
        assertThat(detail.has("paymentStatus")).isFalse();
    }

    CreateAppointmentResponse create(String date, String session, boolean pay) {
        return createFor(81011L, date, session, pay);
    }
    CreateAppointmentResponse createFor(Long patientId, String date, String session, boolean pay) {
        var request = new CreateAppointmentRequest();
        request.setDoctorId(1L); request.setPatientId(patientId);
        request.setAppointmentDate(LocalDate.parse(date)); request.setSessionType(session);
        var response = (CreateAppointmentResponse) bookings.create(81001L, UUID.randomUUID().toString(), request);
        if (pay) payments.pay(81001L, response.getAppointmentOrderId());
        return response;
    }
    void bind(long doctorId, long userId, String token, int expected) throws Exception {
        mvc.perform(put("/api/v1/admin/doctors/" + doctorId + "/account").header("Authorization", token)
                .contentType("application/json").content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().is(expected));
    }
    JsonNode getUser(long id) throws Exception { return getData("/appointments/" + id, owner); }
    JsonNode getData(String path, String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1" + path).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    JsonNode action(String path, String token, int expected) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).header("Authorization", token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).path("data");
    }
    String token(Long id, String... roles) { return "Bearer " + tokens.createToken(id, id + "@visit.example.com", List.of(roles)); }
}

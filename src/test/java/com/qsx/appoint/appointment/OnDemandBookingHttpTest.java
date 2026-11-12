package com.qsx.appoint.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.catalog.model.AppointmentSession;
import com.qsx.appoint.support.MutableAppointmentClock;
import com.qsx.appoint.support.FormalPaymentTestSupport;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.qsx.appoint.support.AppointmentClockTestConfiguration;
import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spring/H2与MockMvc验证三天窗口、截止和支付时间快照；普通用例回滚，NOT_SUPPORTED用例单独清理。 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:on-demand-booking;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import({AppointmentClockTestConfiguration.class, FormalPaymentTestSupport.class})
class OnDemandBookingHttpTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserMapper userMapper;
    @MockitoSpyBean private PatientMapper patientMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private AppointmentOrderMapper appointmentMapper;
    @Autowired private PaymentOrderMapper paymentMapper;
    @Autowired private MutableAppointmentClock clock;
    @Autowired private FormalPaymentTestSupport formalPayments;

    private Long userId;
    private Long patientId;

    @BeforeEach
    void createAccountAndPatient() {
        clock.set("2026-11-05T12:00:00");
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-password-hash");
        userMapper.insert(user);
        userId = user.getId();

        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName("就诊人");
        patient.setDocumentType("RESIDENT_ID_CARD");
        patient.setDocumentNo("110105199001010010");
        patient.setContactPhone("13800000000");
        patient.setContactEmail("patient@example.com");
        patient.setEnabled(true);
        patientMapper.insert(patient);
        patientId = patient.getId();
    }

    @AfterEach
    void cleanCommittedCutoffFixture() {
        // 只有NOT_SUPPORTED用例的账号准备会提交；普通用例仍由测试事务回滚。
        if (!org.springframework.test.context.transaction.TestTransaction.isActive()) {
            patientMapper.deleteById(patientId);
            userMapper.deleteById(userId);
        }
    }

    @Test
    void firstBookingCreatesSessionFromSettingsAndGetsFullPaymentWindow() throws Exception {
        String body = createAppointment("2026-11-06", "MORNING_FIRST", UUID.randomUUID().toString());
        JsonNode data = objectMapper.readTree(body).path("data");
        assertThat(data.path("amount").asText()).isEqualTo("10.00");
        assertThat(data.path("paymentExpiresAt").asText()).isEqualTo("2026-11-05T12:15:00");

        Schedule schedule = scheduleMapper.selectByDoctorDateSession(1L,
                java.time.LocalDate.of(2026, 11, 6), "MORNING_FIRST");
        assertThat(schedule).isNotNull();
        assertThat(schedule.getStartTime()).isEqualTo(java.time.LocalTime.of(8, 0));
        assertThat(schedule.getEndTime()).isEqualTo(java.time.LocalTime.of(10, 0));
        assertThat(schedule.getTotalCapacity()).isEqualTo(20);
        assertThat(schedule.getAvailableCapacity()).isEqualTo(19);
        assertThat(schedule.getHeldCapacity()).isEqualTo(1);
        assertThat(schedule.getRegistrationFee()).isEqualByComparingTo("10.00");
    }

    @Test
    void settingChangesOnlyAffectSessionsCreatedAfterTheChange() throws Exception {
        replaceSettings(30, "12.00");
        createAppointment("2026-11-07", "MORNING_FIRST", UUID.randomUUID().toString());
        replaceSettings(40, "14.00");
        createAppointment("2026-11-07", "MORNING_SECOND", UUID.randomUUID().toString());

        Schedule first = scheduleMapper.selectByDoctorDateSession(1L,
                java.time.LocalDate.of(2026, 11, 7), "MORNING_FIRST");
        Schedule second = scheduleMapper.selectByDoctorDateSession(1L,
                java.time.LocalDate.of(2026, 11, 7), "MORNING_SECOND");
        assertThat(first.getTotalCapacity()).isEqualTo(30);
        assertThat(first.getRegistrationFee()).isEqualByComparingTo("12.00");
        assertThat(second.getTotalCapacity()).isEqualTo(40);
        assertThat(second.getRegistrationFee()).isEqualByComparingTo("14.00");
    }

    @Test
    void newOrdersRejectPastDateCutoffBeyondThreeDatesAndUnknownSession() throws Exception {
        expectDateRejected("2026-11-05", "MORNING_FIRST");
        expectDateRejected("2026-11-04", "AFTERNOON_FIRST");
        expectDateRejected("2026-11-08", "MORNING_FIRST");

        mockMvc.perform(post("/api/v1/appointments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-11-06", "MORNING")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void idempotencyUsesDoctorDateSessionAndPatient() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = createAppointment("2026-11-07", "AFTERNOON_FIRST", key);
        String appointmentId = objectMapper.readTree(first).at("/data/appointmentOrderId").asText();

        mockMvc.perform(post("/api/v1/appointments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-11-07", "AFTERNOON_FIRST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentOrderId").value(appointmentId));

        mockMvc.perform(post("/api/v1/appointments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-11-07", "AFTERNOON_SECOND")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4103));
    }

    @Test
    void todayBookingPersistsIdenticalAppointmentAndPaymentTimes() throws Exception {
        clock.set("2026-11-05T13:40:00");
        var data = objectMapper.readTree(createAppointment("2026-11-05", "AFTERNOON_FIRST",
                UUID.randomUUID().toString())).path("data");
        assertTimes(data, "2026-11-05T13:40:00", "2026-11-05T13:55:00");
    }

    @Test
    void allFourSessionsUseStrictNewOrderCutoffs() throws Exception {
        var date = LocalDate.of(2026, 11, 5);
        for (var session : AppointmentSession.values()) {
            var cutoff = date.atTime(session.startTime()).minusMinutes(15);
            clock.set(cutoff.minusSeconds(1).toString());
            createAppointment(date.toString(), session.name(), UUID.randomUUID().toString());
            clock.set(cutoff.toString());
            expectDateRejected(date.toString(), session.name());
        }
    }

    @Test
    void createdAtIsSampledAfterPatientLockRatherThanRequestRegistration() throws Exception {
        clock.set("2026-11-05T13:30:00");
        advanceAfterPatientLock("2026-11-05T13:40:00");
        var data = objectMapper.readTree(createAppointment("2026-11-05", "AFTERNOON_FIRST",
                UUID.randomUUID().toString())).path("data");
        assertTimes(data, "2026-11-05T13:40:00", "2026-11-05T13:55:00");
        assertThat(data.path("serverTime").asText()).isEqualTo("2026-11-05T13:40:00");
    }

    @Test
    void midnightWhileWaitingStillAcceptsOriginalTomorrowAsToday() throws Exception {
        clock.set("2026-11-05T23:59:59");
        advanceAfterPatientLock("2026-11-06T00:00:00");
        var data = objectMapper.readTree(createAppointment("2026-11-06", "MORNING_FIRST",
                UUID.randomUUID().toString())).path("data");
        assertTimes(data, "2026-11-06T00:00:00", "2026-11-06T00:15:00");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void crossingCutoffAfterPatientLockDoesNotLeaveGeneratedSessionOrOrder() throws Exception {
        long beforeSchedules = scheduleMapper.selectCount(null);
        long beforeOrders = appointmentMapper.selectCount(null);
        clock.set("2026-11-05T13:44:59");
        advanceAfterPatientLock("2026-11-05T13:45:00");
        expectDateRejected("2026-11-05", "AFTERNOON_FIRST");
        assertThat(scheduleMapper.selectCount(null)).isEqualTo(beforeSchedules);
        assertThat(appointmentMapper.selectCount(null)).isEqualTo(beforeOrders);
    }

    @Test
    void sqlAdmissionRejectsExactCutoffAndAcceptsBeforeIt() {
        var date = LocalDate.of(2026, 11, 5);
        var session = AppointmentSession.AFTERNOON_FIRST;
        scheduleMapper.insertGenerated(1L, date, session.startTime(), session.endTime(), session.name(),
                20, new java.math.BigDecimal("10.00"), "CNY");
        var id = scheduleMapper.selectByDoctorDateSession(1L, date, session.name()).getId();
        assertThat(scheduleMapper.holdOneBookableSession(id, date, date.plusDays(2), date,
                java.time.LocalTime.of(14, 0))).isZero();
        assertThat(scheduleMapper.holdOneBookableSession(id, date, date.plusDays(2), date,
                java.time.LocalTime.of(13, 59, 59))).isEqualTo(1);
        assertThat(scheduleMapper.selectById(id).getHeldCapacity()).isEqualTo(1);
    }

    private void advanceAfterPatientLock(String localTime) {
        var originalAnswer = org.mockito.Mockito.mockingDetails(patientMapper)
                .getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            // MyBatis Mapper 是 JDK 代理，沿用 spy 的真实委托，而不是调用接口抽象方法。
            Object patient = originalAnswer.answer(invocation);
            clock.set(localTime);
            return patient;
        }).when(patientMapper).selectByIdForUpdate(patientId);
    }

    @Test
    void existingOrderCanPayAfterNewBookingCutoffWithOneDeadlineAcrossAllResponses() throws Exception {
        clock.set("2026-11-05T10:14:00");
        var created = objectMapper.readTree(createAppointment("2026-11-05", "MORNING_SECOND",
                UUID.randomUUID().toString())).path("data");
        String expiresAt = created.path("paymentExpiresAt").asText();
        assertThat(expiresAt).isEqualTo("2026-11-05T10:29:00");
        clock.set("2026-11-05T10:20:00");
        expectDateRejected("2026-11-05", "MORNING_SECOND");
        var attempt = startPayment(created);
        assertThat(attempt.path("expiresAt").asText()).isEqualTo(expiresAt);
        var detail = detail(created);
        assertThat(detail.path("paymentExpiresAt").asText()).isEqualTo(expiresAt);
        var confirmed = formalPayments.pay(userId, created.path("paymentOrderId").asLong());
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.getPaymentStatus()).isEqualTo("PAID");
        assertThat(confirmed.getVisitNumber()).isPositive();
        assertThat(detail(created).path("paymentExpiresAt").asText()).isEqualTo(expiresAt);
    }

    @Test
    void paymentAcrossMidnightUsesPersistedDeadline() throws Exception {
        clock.set("2026-11-05T23:55:00");
        var created = objectMapper.readTree(createAppointment("2026-11-06", "MORNING_FIRST",
                UUID.randomUUID().toString())).path("data");
        assertThat(created.path("paymentExpiresAt").asText()).isEqualTo("2026-11-06T00:10:00");
        clock.set("2026-11-06T00:05:00");
        assertThat(startPayment(created).path("expiresAt").asText()).isEqualTo("2026-11-06T00:10:00");
        formalPayments.pay(userId, created.path("paymentOrderId").asLong());
        assertThat(detail(created).path("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void historyRemainsReadableAfterLeavingTheThreeDayWindowWithoutRecalculatingDeadline() throws Exception {
        var created = objectMapper.readTree(createAppointment("2026-11-07", "MORNING_FIRST",
                UUID.randomUUID().toString())).path("data");
        String expiresAt = created.path("paymentExpiresAt").asText();
        clock.set("2026-11-10T10:00:00");
        assertThat(detail(created).path("paymentExpiresAt").asText()).isEqualTo(expiresAt);
        var response = mockMvc.perform(get("/api/v1/appointments/my").with(authentication(auth())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var rows = objectMapper.readTree(response).path("data");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).path("id").asText()).isEqualTo(created.path("appointmentOrderId").asText());
        assertThat(rows.get(0).path("paymentExpiresAt").asText()).isEqualTo(expiresAt);
    }

    @Test
    void reachingPaymentDeadlineDisablesPaymentWithoutWaitingForExpirationScan() throws Exception {
        clock.set("2026-11-05T10:14:00");
        var created = objectMapper.readTree(createAppointment("2026-11-05", "MORNING_SECOND",
                UUID.randomUUID().toString())).path("data");
        clock.set("2026-11-05T10:29:00");
        var detail = detail(created);
        assertThat(detail.path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(detail.path("paymentExpired").asBoolean()).isTrue();
        assertThat(detail.path("canPay").asBoolean()).isFalse();
        mockMvc.perform(post("/api/v1/payments/{id}/attempts", created.path("paymentOrderId").asText())
                        .with(authentication(auth())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"MOCK\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(4004));
        assertThat(detail(created).path("paymentExpiresAt")).isEqualTo(created.path("paymentExpiresAt"));
    }

    private JsonNode startPayment(JsonNode created) throws Exception {
        var response = mockMvc.perform(post("/api/v1/payments/{id}/attempts", created.path("paymentOrderId").asText())
                        .with(authentication(auth())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"MOCK\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode detail(JsonNode created) throws Exception {
        var response = mockMvc.perform(get("/api/v1/appointments/{id}", created.path("appointmentOrderId").asText())
                        .with(authentication(auth())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private void assertTimes(JsonNode data, String createdAt, String expiresAt) {
        var appointment = appointmentMapper.selectById(data.path("appointmentOrderId").asLong());
        var payment = paymentMapper.selectById(data.path("paymentOrderId").asLong());
        assertThat(appointment.getCreatedAt()).isEqualTo(LocalDateTime.parse(createdAt));
        assertThat(payment.getCreatedAt()).isEqualTo(appointment.getCreatedAt());
        assertThat(appointment.getPaymentExpiresAt()).isEqualTo(LocalDateTime.parse(expiresAt));
        assertThat(payment.getExpiresAt()).isEqualTo(appointment.getPaymentExpiresAt());
    }

    private String createAppointment(String date, String session, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/appointments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(date, session)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.appointmentStatus").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
    }

    private void expectDateRejected(String date, String session) throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(date, session)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4202));
    }

    private void replaceSettings(int capacity, String fee) throws Exception {
        mockMvc.perform(put("/api/v1/admin/booking-settings")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacityPerSession\":" + capacity
                                + ",\"registrationFee\":" + fee + "}"))
                .andExpect(status().isOk());
    }

    private String request(String date, String session) {
        return "{\"doctorId\":\"1\",\"appointmentDate\":\"" + date
                + "\",\"sessionType\":\"" + session + "\",\"patientId\":\""
                + patientId + "\"}";
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken(userId, null, "ROLE_USER");
    }

}

package com.qsx.appoint.appointment;

import static com.qsx.appoint.support.BookingTestToken.issueBookingToken;
import static com.qsx.appoint.support.RegistrationTestSupport.verifiedBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.support.BookingTestDoctors;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.qsx.appoint.support.CommittedFixtureScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spring/H2验证幂等重放、同键冲突、账户隔离及不可变收据；真实提交后按本例账号清理。 */
@RegressionTest(properties = "spring.datasource.url=jdbc:h2:mem:appointment-idempotency-integration-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("regression")
@org.springframework.context.annotation.Import(com.qsx.appoint.support.FormalPaymentTestSupport.class)
class AppointmentIdempotencyIntegrationTest {
    @Autowired private JdbcTemplate fixtureJdbc;
    private CommittedFixtureScope fixtureScope;

    @BeforeEach
    void trackCommittedFixture() { fixtureScope = new CommittedFixtureScope(fixtureJdbc); }

    @AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Autowired private com.qsx.appoint.support.FormalPaymentTestSupport formalPayments;
    private static final AtomicInteger SCHEDULE_SEQUENCE = new AtomicInteger();
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private DoctorMapper doctorMapper;
    @Autowired private AppointmentOrderMapper appointmentOrderMapper;
    @Autowired private BookingLifecycleCoordinator lifecycleService;

    @Test
    void sameKeyReturnsReplayReceiptAndAppointmentKeepsPatientSnapshot() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "原姓名", "110105199001010010");
        Long scheduleId = createSchedule(LocalDate.of(2099, 8, 1), LocalTime.of(8, 0));
        String key = issueBookingToken(mockMvc, "Bearer " + token).toUpperCase();
        String request = appointmentBody(scheduleId, patientId);

        String first = createAppointment(token, key, request);
        JsonNode firstData = objectMapper.readTree(first).path("data");
        assertThat(firstData.path("appointmentNo").asText()).startsWith("AP");
        assertThat(firstData.path("paymentNo").asText()).startsWith("PAY");
        assertThat(firstData.path("createdAt").asText()).isNotBlank();
        assertThat(firstData.path("appointmentStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(firstData.path("paymentStatus").asText()).isEqualTo("UNPAID");
        assertThat(firstData.path("amount").asText()).isEqualTo("50.00");
        assertThat(firstData.path("currency").asText()).isEqualTo("CNY");
        String appointmentId = firstData.path("appointmentOrderId").asText();
        assertReplayReceipt(replayAppointment(token, key.toLowerCase(), request), first);

        mockMvc.perform(patch("/api/v1/patients/" + patientId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新姓名\",\"contactEmail\":\"new@example.com\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patient.id").value(patientId))
                .andExpect(jsonPath("$.data.patient.name").value("原姓名"))
                .andExpect(jsonPath("$.data.patient.documentNoMasked").value("110105********0010"))
                .andExpect(jsonPath("$.data.patient.contactEmail").value("patient@example.com"));
        assertReplayReceipt(replayAppointment(token, key, request), first);
    }

    @Test
    void sameKeyWithDifferentRequestReturnsConflict() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");
        Long firstScheduleId = createSchedule(LocalDate.of(2099, 8, 2), LocalTime.of(8, 0));
        Long secondScheduleId = createSchedule(LocalDate.of(2099, 8, 2), LocalTime.of(14, 0));
        String key = issueBookingToken(mockMvc, "Bearer " + token);

        createAppointment(token, key, appointmentBody(firstScheduleId, patientId));
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(secondScheduleId, patientId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4103));
    }

    @Test
    void failedRequestRequiresNewTokenAfterPatientIsEnabled() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");
        Long scheduleId = createSchedule(LocalDate.of(2099, 8, 3), LocalTime.of(8, 0));
        String key = issueBookingToken(mockMvc, "Bearer " + token);
        setPatientEnabled(token, patientId, false);

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(scheduleId, patientId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4102));

        setPatientEnabled(token, patientId, true);
        String body = appointmentBody(scheduleId, patientId);
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4104));
        createAppointment(token, issueBookingToken(mockMvc, "Bearer " + token), body);
    }

    @Test
    void patientOwnershipIsEnforcedAndSameKeyIsScopedByAccount() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        String ownerPatientId = createPatient(ownerToken, "本人", "110105199001010010");
        String otherPatientId = createPatient(otherToken, "他人", "110105199002020026");
        Long forbiddenScheduleId = createSchedule(LocalDate.of(2099, 8, 4), LocalTime.of(8, 0));
        Long ownerScheduleId = createSchedule(LocalDate.of(2099, 8, 4), LocalTime.of(14, 0));
        Long otherScheduleId = createSchedule(LocalDate.of(2099, 8, 5), LocalTime.of(8, 0));
        String sharedKey = issueBookingToken(mockMvc, "Bearer " + ownerToken);

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + otherToken)
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(otherScheduleId, otherPatientId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4104));
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + otherToken)
                        .header("Idempotency-Key", issueBookingToken(mockMvc, "Bearer " + otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(forbiddenScheduleId, ownerPatientId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));

        createAppointment(ownerToken, sharedKey, appointmentBody(ownerScheduleId, ownerPatientId));
        createAppointment(otherToken, issueBookingToken(mockMvc, "Bearer " + otherToken),
                appointmentBody(otherScheduleId, otherPatientId));
    }

    @Test
    void replayAfterCancellationAndPatientDisableStillReturnsOriginalAppointmentReceipt() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");
        Long scheduleId = createSchedule(LocalDate.of(2099, 8, 8), LocalTime.of(8, 0));
        String key = issueBookingToken(mockMvc, "Bearer " + token);
        String body = appointmentBody(scheduleId, patientId);
        String first = createAppointment(token, key, body);
        String appointmentId = objectMapper.readTree(first).at("/data/appointmentOrderId").asText();

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        setPatientEnabled(token, patientId, false);

        assertReplayReceipt(replayAppointment(token, key, body), first);
    }

    @Test
    void replayAfterPaymentOrExpirationStillReturnsOriginalAppointmentReceipt() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");

        Long paidScheduleId = createSchedule(LocalDate.of(2099, 8, 7), LocalTime.of(8, 0));
        String paidKey = issueBookingToken(mockMvc, "Bearer " + token);
        String paidBody = appointmentBody(paidScheduleId, patientId);
        String paidFirst = createAppointment(token, paidKey, paidBody);
        String paidAppointmentId = objectMapper.readTree(paidFirst).at("/data/appointmentOrderId").asText();
        assertThat(formalPayments.payWithToken(token, Long.valueOf(paidAppointmentId))).satisfies(paid -> {
            assertThat(paid.getStatus()).isEqualTo("CONFIRMED");
            assertThat(paid.getPaymentStatus()).isEqualTo("PAID");
        });
        assertReplayReceipt(replayAppointment(token, paidKey, paidBody), paidFirst);

        Long expiredScheduleId = createSchedule(LocalDate.of(2099, 8, 7), LocalTime.of(14, 0));
        String expiredKey = issueBookingToken(mockMvc, "Bearer " + token);
        String expiredBody = appointmentBody(expiredScheduleId, patientId);
        String expiredFirst = createAppointment(token, expiredKey, expiredBody);
        Long appointmentId = objectMapper.readTree(expiredFirst).at("/data/appointmentOrderId").asLong();
        AppointmentOrder appointment = appointmentOrderMapper.selectById(appointmentId);
        appointment.setPaymentExpiresAt(appointment.getCreatedAt().minusSeconds(1));
        appointmentOrderMapper.updateById(appointment);
        assertThat(lifecycleService.expireIfDue(appointmentId)).isTrue();
        assertReplayReceipt(replayAppointment(token, expiredKey, expiredBody), expiredFirst);
    }

    @Test
    void missingOrInvalidIdempotencyKeyIsRejected() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");
        Long scheduleId = createSchedule(LocalDate.of(2099, 8, 6), LocalTime.of(8, 0));
        String body = appointmentBody(scheduleId, patientId);

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    private String createAppointment(String token, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String replayAppointment(String token, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void assertReplayReceipt(String response, String firstResponse) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        assertThat(root.path("code").asInt()).isZero();
        assertThat(root.path("message").asText()).isEqualTo("该请求已创建预约，请查询详情获取当前状态");
        assertThat(root.path("data")).isEqualTo(objectMapper.readTree(firstResponse).path("data"));
    }

    private String createPatient(String token, String name, String documentNo) throws Exception {
        String body = """
                {"name":"%s","documentType":"RESIDENT_ID_CARD","documentNo":"%s",
                 "contactPhone":"13800138000","contactEmail":"patient@example.com"}
                """.formatted(name, documentNo);
        String response = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asText();
    }

    private void setPatientEnabled(String token, String patientId, boolean enabled) throws Exception {
        mockMvc.perform(patch("/api/v1/patients/" + patientId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(enabled));
    }

    private String appointmentBody(Long scheduleId, String patientId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        return "{\"doctorId\":\"" + schedule.getDoctorId()
                + "\",\"appointmentDate\":\"" + schedule.getAppointmentDate()
                + "\",\"sessionType\":\"" + schedule.getSessionType()
                + "\",\"patientId\":\"" + patientId + "\"}";
    }

    private String registerAndLogin() throws Exception {
        String email = "appointment-idempotency-" + UUID.randomUUID() + "@example.com";
        String credentials = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(verifiedBody(mockMvc, credentials)))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private Long createSchedule(LocalDate date, LocalTime start) {
        int sequence = SCHEDULE_SEQUENCE.getAndIncrement();
        Schedule schedule = new Schedule();
        schedule.setDoctorId(BookingTestDoctors.copyOf(doctorMapper, 2L));
        schedule.setAppointmentDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(2));
        boolean afternoon = sequence % 2 == 1;
        schedule.setStartTime(afternoon ? LocalTime.of(14, 0) : LocalTime.of(8, 0));
        schedule.setEndTime(afternoon ? LocalTime.of(16, 0) : LocalTime.of(10, 0));
        schedule.setStatus("OPEN");
        schedule.setSessionType(afternoon ? "AFTERNOON_FIRST" : "MORNING_FIRST");
        schedule.setTotalCapacity(5);
        schedule.setRegistrationFee(new java.math.BigDecimal("50.00"));
        schedule.setFeeCurrency("CNY");
        schedule.setAvailableCapacity(5);
        schedule.setHeldCapacity(0);
        schedule.setConfirmedCapacity(0);
        schedule.setNextVisitNumber(1);
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }
}

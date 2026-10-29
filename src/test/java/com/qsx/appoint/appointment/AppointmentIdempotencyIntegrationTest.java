package com.qsx.appoint.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentIdempotencyIntegrationTest {
    private static final AtomicInteger SCHEDULE_SEQUENCE = new AtomicInteger();
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private AppointmentOrderMapper appointmentOrderMapper;
    @Autowired private AppointmentLifecycleService lifecycleService;

    @Test
    void sameKeyReturnsReplayReceiptAndAppointmentKeepsPatientSnapshot() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "原姓名", "110105199001010010");
        Long scheduleId = createSchedule(LocalDate.of(2099, 8, 1), LocalTime.of(8, 0));
        String key = UUID.randomUUID().toString().toUpperCase();
        String request = "{\"scheduleId\":\"" + scheduleId + "\",\"patientId\":\"" + patientId + "\"}";

        String first = createAppointment(token, key, request);
        JsonNode firstData = objectMapper.readTree(first).path("data");
        assertThat(firstData.size()).isEqualTo(6);
        assertThat(firstData.has("replayed")).isFalse();
        assertThat(firstData.path("appointmentStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(firstData.path("paymentStatus").asText()).isEqualTo("UNPAID");
        String appointmentId = firstData.path("appointmentOrderId").asText();
        String appointmentNo = appointmentOrderMapper.selectById(Long.valueOf(appointmentId)).getAppointmentNo();
        assertReplayReceipt(replayAppointment(token, key.toLowerCase(), request), appointmentId, appointmentNo);

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
        assertReplayReceipt(replayAppointment(token, key, request), appointmentId, appointmentNo);
    }

    @Test
    void sameKeyWithDifferentRequestReturnsConflict() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");
        Long firstScheduleId = createSchedule(LocalDate.of(2099, 8, 2), LocalTime.of(8, 0));
        Long secondScheduleId = createSchedule(LocalDate.of(2099, 8, 2), LocalTime.of(14, 0));
        String key = UUID.randomUUID().toString();

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
    void failedRequestDoesNotConsumeKeyAndDisabledPatientCanRetryAfterEnable() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");
        Long scheduleId = createSchedule(LocalDate.of(2099, 8, 3), LocalTime.of(8, 0));
        String key = UUID.randomUUID().toString();
        setPatientEnabled(token, patientId, false);

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(scheduleId, patientId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4102));

        setPatientEnabled(token, patientId, true);
        createAppointment(token, key, appointmentBody(scheduleId, patientId));
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
        String sharedKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + otherToken)
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(forbiddenScheduleId, ownerPatientId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));

        createAppointment(ownerToken, sharedKey, appointmentBody(ownerScheduleId, ownerPatientId));
        createAppointment(otherToken, sharedKey, appointmentBody(otherScheduleId, otherPatientId));
    }

    @Test
    void replayAfterCancellationAndPatientDisableStillReturnsOriginalAppointmentReceipt() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");
        Long scheduleId = createSchedule(LocalDate.of(2099, 8, 8), LocalTime.of(8, 0));
        String key = UUID.randomUUID().toString();
        String body = appointmentBody(scheduleId, patientId);
        String first = createAppointment(token, key, body);
        String appointmentId = objectMapper.readTree(first).at("/data/appointmentOrderId").asText();
        String appointmentNo = appointmentOrderMapper.selectById(Long.valueOf(appointmentId)).getAppointmentNo();

        mockMvc.perform(post("/api/v1/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        setPatientEnabled(token, patientId, false);

        assertReplayReceipt(replayAppointment(token, key, body), appointmentId, appointmentNo);
    }

    @Test
    void replayAfterPaymentOrExpirationStillReturnsOriginalAppointmentReceipt() throws Exception {
        String token = registerAndLogin();
        String patientId = createPatient(token, "就诊人", "110105199001010010");

        Long paidScheduleId = createSchedule(LocalDate.of(2099, 8, 7), LocalTime.of(8, 0));
        String paidKey = UUID.randomUUID().toString();
        String paidBody = appointmentBody(paidScheduleId, patientId);
        String paidFirst = createAppointment(token, paidKey, paidBody);
        String paidAppointmentId = objectMapper.readTree(paidFirst).at("/data/appointmentOrderId").asText();
        String paidAppointmentNo = appointmentOrderMapper.selectById(Long.valueOf(paidAppointmentId)).getAppointmentNo();
        String paymentId = objectMapper.readTree(paidFirst).at("/data/paymentOrderId").asText();
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/simulate-success")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentStatus").value("CONFIRMED"));
        assertReplayReceipt(replayAppointment(token, paidKey, paidBody), paidAppointmentId, paidAppointmentNo);

        Long expiredScheduleId = createSchedule(LocalDate.of(2099, 8, 7), LocalTime.of(14, 0));
        String expiredKey = UUID.randomUUID().toString();
        String expiredBody = appointmentBody(expiredScheduleId, patientId);
        String expiredFirst = createAppointment(token, expiredKey, expiredBody);
        Long appointmentId = objectMapper.readTree(expiredFirst).at("/data/appointmentOrderId").asLong();
        AppointmentOrder appointment = appointmentOrderMapper.selectById(appointmentId);
        String appointmentNo = appointment.getAppointmentNo();
        appointment.setPaymentExpiresAt(appointment.getCreatedAt().minusSeconds(1));
        appointmentOrderMapper.updateById(appointment);
        assertThat(lifecycleService.expireIfDue(appointmentId)).isTrue();
        assertReplayReceipt(replayAppointment(token, expiredKey, expiredBody), appointmentId.toString(), appointmentNo);
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

    private void assertReplayReceipt(String response, String appointmentId, String appointmentNo) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        assertThat(root.path("code").asInt()).isZero();
        assertThat(root.path("message").asText()).isEqualTo("该请求已创建预约，请查询详情获取当前状态");
        JsonNode data = root.path("data");
        assertThat(data.size()).isEqualTo(3);
        assertThat(data.path("appointmentOrderId").asText()).isEqualTo(appointmentId);
        assertThat(data.path("appointmentNo").asText()).isEqualTo(appointmentNo);
        assertThat(data.path("replayed").asBoolean()).isTrue();
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
        return "{\"scheduleId\":\"" + scheduleId + "\",\"patientId\":\"" + patientId + "\"}";
    }

    private String registerAndLogin() throws Exception {
        String email = "appointment-idempotency-" + UUID.randomUUID() + "@example.com";
        String credentials = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private Long createSchedule(LocalDate date, LocalTime start) {
        int sequence = SCHEDULE_SEQUENCE.getAndIncrement();
        Schedule schedule = new Schedule();
        schedule.setDoctorId(2L);
        schedule.setAppointmentDate(LocalDate.now().plusDays(1 + sequence / 2));
        boolean afternoon = sequence % 2 == 1;
        schedule.setStartTime(afternoon ? LocalTime.of(14, 0) : LocalTime.of(8, 0));
        schedule.setEndTime(afternoon ? LocalTime.of(17, 0) : LocalTime.of(12, 0));
        schedule.setStatus("OPEN");
        schedule.setSessionType(afternoon ? "AFTERNOON" : "MORNING");
        schedule.setTotalCapacity(5);
        schedule.setAvailableCapacity(5);
        schedule.setHeldCapacity(0);
        schedule.setConfirmedCapacity(0);
        schedule.setNextVisitNumber(1);
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }
}

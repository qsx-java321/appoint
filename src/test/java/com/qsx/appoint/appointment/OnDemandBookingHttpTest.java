package com.qsx.appoint.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:on-demand-booking;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Transactional
@Import(OnDemandBookingHttpTest.FixedClockConfig.class)
class OnDemandBookingHttpTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private ScheduleMapper scheduleMapper;

    private Long userId;
    private Long patientId;

    @BeforeEach
    void createAccountAndPatient() {
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
    void newOrdersRejectTodayBeyondSevenDaysAndUnknownSession() throws Exception {
        expectDateRejected("2026-11-05", "MORNING_FIRST");
        expectDateRejected("2026-11-13", "MORNING_FIRST");

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
        String first = createAppointment("2026-11-08", "AFTERNOON_FIRST", key);
        String appointmentId = objectMapper.readTree(first).at("/data/appointmentOrderId").asText();

        mockMvc.perform(post("/api/v1/appointments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-11-08", "AFTERNOON_FIRST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentOrderId").value(appointmentId));

        mockMvc.perform(post("/api/v1/appointments")
                        .with(authentication(auth()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-11-08", "AFTERNOON_SECOND")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4103));
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

    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedAppointmentClock() {
            return Clock.fixed(Instant.parse("2026-11-05T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}

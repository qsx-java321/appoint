package com.qsx.appoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.support.BookingTestDoctors;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import com.qsx.appoint.payment.port.GatewayPaymentStatus;
import com.qsx.appoint.payment.port.RawPaymentNotification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.qsx.appoint.support.CommittedFixtureScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用真实 Spring MVC、安全链、Flyway 和 H2 验证核心接口闭环。
 * 该测试不依赖外部 Docker 服务，因此可快速发现认证或事务配置错误。
 */
@RegressionTest(properties =
        "spring.datasource.url=jdbc:h2:mem:api-flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("regression")
@org.springframework.context.annotation.Import(com.qsx.appoint.support.FormalPaymentTestSupport.class)
class ApiFlowIntegrationTest {
    @Autowired private JdbcTemplate fixtureJdbc;
    private CommittedFixtureScope fixtureScope;

    @BeforeEach
    void trackCommittedFixture() { fixtureScope = new CommittedFixtureScope(fixtureJdbc); }

    @AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Autowired private com.qsx.appoint.support.FormalPaymentTestSupport formalPayments;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private DoctorMapper doctorMapper;
    @Autowired private MockPaymentGateway mockPaymentGateway;
    @Autowired private PaymentOrderMapper paymentOrderMapper;
    @Autowired private PaymentOutboxEventMapper outboxEventMapper;
    @Autowired private Clock appointmentClock;

    @Test
    void registerLoginAndReadCurrentUser() throws Exception {
        String email = "flow-user@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value(email));

        String loginResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResult).at("/data/accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        String body = "{\"email\":\"duplicate@example.com\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    void wrongPasswordAndMissingTokenAreRejected() throws Exception {
        String body = "{\"email\":\"wrong-password@example.com\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong-password@example.com\",\"password\":\"incorrect123\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(2003));
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void doctorCatalogExposesGlobalDoctorNumbers() throws Exception {
        String body = "{\"email\":\"catalog-user@example.com\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
        String loginResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResult).at("/data/accessToken").asText();

        mockMvc.perform(get("/api/v1/departments/1/doctors").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].doctorNo").value("DOC000001"));
        mockMvc.perform(get("/api/v1/departments/2/doctors").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("2"))
                .andExpect(jsonPath("$.data[0].doctorNo").value("DOC000002"));
    }

    @Test
    void responseIdsAreStringsAndStringScheduleIdCanCreateAppointment() throws Exception {
        String body = "{\"email\":\"string-id-user@example.com\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString());
        String loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").isString())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResult).at("/data/accessToken").asText();
        String patientId = createPatient(token);
        Long scheduleId = createSchedule(LocalDate.now(appointmentClock).plusDays(2));

        mockMvc.perform(get("/api/v1/hospitals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].id").value("1"));

        mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(scheduleId, patientId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.appointmentOrderId").isString())
                .andExpect(jsonPath("$.data.paymentOrderId").isString());
    }

    @Test
    void appointmentAndPaymentAdvanceStatesOnlyOnce() throws Exception {
        String email = "order-user@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
        String loginResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResult).at("/data/accessToken").asText();
        String patientId = createPatient(token);
        Long scheduleId = createSchedule(LocalDate.now(appointmentClock).plusDays(2));

        String appointmentResult = mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(scheduleId, patientId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.appointmentStatus").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(appointmentResult).get("data");
        long paymentId = data.get("paymentOrderId").asLong();

        assertThat(formalPayments.payWithToken(token, paymentId)).satisfies(paid -> {
            assertThat(paid.getStatus()).isEqualTo("CONFIRMED");
            assertThat(paid.getPaymentStatus()).isEqualTo("PAID");
        });
        assertThat(formalPayments.payWithToken(token, paymentId)).satisfies(paid -> {
            assertThat(paid.getStatus()).isEqualTo("CONFIRMED");
            assertThat(paid.getPaymentStatus()).isEqualTo("PAID");
        });

        mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(scheduleId, patientId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(4204));
    }

    @Test
    void startingMockPaymentAttemptReturnsAcceptedAndStableCheckoutData() throws Exception {
        String body = "{\"email\":\"attempt-http@example.com\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
        String loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResult).at("/data/accessToken").asText();
        String patientId = createPatient(token);
        Long scheduleId = createSchedule(LocalDate.now(appointmentClock).plusDays(2));
        String appointmentResult = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(scheduleId, patientId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String paymentId = objectMapper.readTree(appointmentResult).at("/data/paymentOrderId").asText();

        String attemptResult = mockMvc.perform(post("/api/v1/payments/{id}/attempts", paymentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"MOCK\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.paymentOrderId").value(paymentId))
                .andExpect(jsonPath("$.data.channel").value("MOCK"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAYING"))
                .andExpect(jsonPath("$.data.attemptStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.checkoutUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String providerOrderNo = objectMapper.readTree(attemptResult).at("/data/providerOrderNo").asText();

        RawPaymentNotification amountMismatch = mockPaymentGateway.signedNotification(
                "notify-wrong-" + paymentId, providerOrderNo, "mock-txn-wrong",
                GatewayPaymentStatus.SUCCEEDED, new BigDecimal("49.99"), "CNY",
                LocalDateTime.now(appointmentClock));
        mockMvc.perform(post("/api/v1/payment-notifications/MOCK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(amountMismatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingResult").value("REJECTED_AMOUNT_OR_CURRENCY"));
        assertThat(paymentOrderMapper.selectById(Long.valueOf(paymentId)).getStatus()).isEqualTo("PAYING");

        RawPaymentNotification success = mockPaymentGateway.complete(
                providerOrderNo, "notify-success-" + paymentId, LocalDateTime.now(appointmentClock));
        String successBody = objectMapper.writeValueAsString(success);
        mockMvc.perform(post("/api/v1/payment-notifications/MOCK")
                        .contentType(MediaType.APPLICATION_JSON).content(successBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingResult").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
        mockMvc.perform(post("/api/v1/payment-notifications/MOCK")
                        .contentType(MediaType.APPLICATION_JSON).content(successBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true));
        assertThat(paymentOrderMapper.selectById(Long.valueOf(paymentId)).getStatus()).isEqualTo("PAID");
        assertThat(outboxEventMapper.selectList(null)).hasSize(1);

        RawPaymentNotification conflicting = mockPaymentGateway.signedNotification(
                success.notificationId(), success.providerOrderNo(), success.providerTransactionNo(),
                success.status(), success.amount(), success.currency(), success.occurredAt().plusSeconds(1));
        mockMvc.perform(post("/api/v1/payment-notifications/MOCK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflicting)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4103));

        RawPaymentNotification lateFailure = mockPaymentGateway.signedNotification(
                "notify-late-failure-" + paymentId, success.providerOrderNo(), success.providerTransactionNo(),
                GatewayPaymentStatus.FAILED, success.amount(), success.currency(), success.occurredAt().plusSeconds(2));
        mockMvc.perform(post("/api/v1/payment-notifications/MOCK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lateFailure)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingResult").value("IGNORED_LATE_FAILURE"));
        assertThat(paymentOrderMapper.selectById(Long.valueOf(paymentId)).getStatus()).isEqualTo("PAID");
        assertThat(outboxEventMapper.selectList(null)).hasSize(1);

        RawPaymentNotification forged = new RawPaymentNotification(success.notificationId(), success.providerOrderNo(),
                success.providerTransactionNo(), success.status(), success.amount(), success.currency(),
                success.occurredAt(), success.signature() + "00");
        mockMvc.perform(post("/api/v1/payment-notifications/MOCK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forged)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4302));
    }

    private String createPatient(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"流程就诊人\",\"documentType\":\"RESIDENT_ID_CARD\"," +
                                "\"documentNo\":\"110105199001010010\",\"contactPhone\":\"13800138000\"," +
                                "\"contactEmail\":\"patient@example.com\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asText();
    }

    private Long createSchedule(LocalDate date) {
        Schedule schedule = new Schedule();
        schedule.setDoctorId(BookingTestDoctors.copyOf(doctorMapper, 1L));
        schedule.setAppointmentDate(date);
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setStatus("OPEN");
        schedule.setSessionType("MORNING_FIRST");
        schedule.setTotalCapacity(2);
        schedule.setRegistrationFee(new java.math.BigDecimal("50.00"));
        schedule.setFeeCurrency("CNY");
        schedule.setAvailableCapacity(2);
        schedule.setHeldCapacity(0);
        schedule.setConfirmedCapacity(0);
        schedule.setNextVisitNumber(1);
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }

    private String appointmentBody(Long scheduleId, String patientId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        return "{\"doctorId\":\"" + schedule.getDoctorId()
                + "\",\"appointmentDate\":\"" + schedule.getAppointmentDate()
                + "\",\"sessionType\":\"" + schedule.getSessionType()
                + "\",\"patientId\":\"" + patientId + "\"}";
    }
}

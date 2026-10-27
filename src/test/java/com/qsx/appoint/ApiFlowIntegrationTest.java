package com.qsx.appoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用真实 Spring MVC、安全链、Flyway 和 H2 验证核心接口闭环。
 * 该测试不依赖外部 Docker 服务，因此可快速发现认证或事务配置错误。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

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

        mockMvc.perform(get("/api/v1/hospitals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].id").value("1"));

        mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":\"2\",\"patientId\":\"" + patientId + "\"}"))
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

        String appointmentResult = mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":1,\"patientId\":\"" + patientId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.appointmentStatus").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(appointmentResult).get("data");
        long paymentId = data.get("paymentOrderId").asLong();

        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", paymentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.appointmentStatus").value("CONFIRMED"));
        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", paymentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        mockMvc.perform(post("/api/v1/appointments").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":1,\"patientId\":\"" + patientId + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(4002));
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
}

package com.qsx.appoint.patient;

import static com.qsx.appoint.support.RegistrationTestSupport.verifiedBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spring/H2与MockMvc验证就诊人身份、所有权和启停；同步用例由测试事务回滚。 */
@RegressionTest
@org.springframework.transaction.annotation.Transactional
@AutoConfigureMockMvc
class PatientIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void userCanCreateAndRetrievePatientWithNormalizedMaskedIdentity() throws Exception {
        String token = registerAndLogin();

        String response = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " 张三 ",
                                  "documentType": "RESIDENT_ID_CARD",
                                  "documentNo": "11010519491231002x",
                                  "contactPhone": " 13800138000 ",
                                  "contactEmail": " Patient@Example.COM "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andExpect(jsonPath("$.data.documentType").value("RESIDENT_ID_CARD"))
                .andExpect(jsonPath("$.data.documentNoMasked").value("110105********002X"))
                .andExpect(jsonPath("$.data.contactPhone").value("13800138000"))
                .andExpect(jsonPath("$.data.contactEmail").value("patient@example.com"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(response).path("data");
        mockMvc.perform(get("/api/v1/patients/" + created.path("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentNoMasked").value("110105********002X"));
    }

    @Test
    void documentIsUniqueWithinAccountButCanBeRegisteredByAnotherAccount() throws Exception {
        String firstToken = registerAndLogin();
        String secondToken = registerAndLogin();
        String patient = patientBody("甲", "110105199001010010");

        String first = mockMvc.perform(post("/api/v1/patients").header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON).content(patient))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String firstPatientId = objectMapper.readTree(first).at("/data/id").asText();
        mockMvc.perform(patch("/api/v1/patients/" + firstPatientId + "/status")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/patients").header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON).content(patient))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4101));
        String second = mockMvc.perform(post("/api/v1/patients").header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody("乙", "110105199002020026")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String secondPatientId = objectMapper.readTree(second).at("/data/id").asText();
        mockMvc.perform(patch("/api/v1/patients/" + secondPatientId)
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentNo\":\"110105199001010010\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4101));
        mockMvc.perform(get("/api/v1/patients").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(secondPatientId))
                .andExpect(jsonPath("$.data[1].id").value(firstPatientId));
        mockMvc.perform(post("/api/v1/patients").header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON).content(patient))
                .andExpect(status().isCreated());
    }

    @Test
    void ownerCanUpdateDisableAndEnablePatientWhileOtherUserIsForbidden() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        String createdBody = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody("原姓名", "110105199002020026")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String patientId = objectMapper.readTree(createdBody).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/patients/" + patientId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/patients/" + patientId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"越权修改\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/patients/" + patientId + "/status")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/patients/" + patientId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新姓名\",\"contactPhone\":\"13900139000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新姓名"))
                .andExpect(jsonPath("$.data.contactPhone").value("13900139000"));

        mockMvc.perform(patch("/api/v1/patients/" + patientId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        mockMvc.perform(patch("/api/v1/patients/" + patientId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void invalidIdentityAndEmptyPatchFieldsAreRejected() throws Exception {
        String token = registerAndLogin();
        mockMvc.perform(post("/api/v1/patients").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody("错误证件", "110105199001010011")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        mockMvc.perform(post("/api/v1/patients").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody("错误日期", "110105199013010010")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));

        String created = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody("可修改", "310101198805060037")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String patientId = objectMapper.readTree(created).at("/data/id").asText();
        mockMvc.perform(patch("/api/v1/patients/" + patientId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
    }

    private String registerAndLogin() throws Exception {
        String email = "patient-" + UUID.randomUUID() + "@example.com";
        String credentials = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(verifiedBody(mockMvc, credentials)))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(login).at("/data/accessToken").asText();
    }

    private String patientBody(String name, String documentNo) {
        return """
                {"name":"%s","documentType":"RESIDENT_ID_CARD","documentNo":"%s",
                 "contactPhone":"13800138000","contactEmail":"patient@example.com"}
                """.formatted(name, documentNo);
    }
}

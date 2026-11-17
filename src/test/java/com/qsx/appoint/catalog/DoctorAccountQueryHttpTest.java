package com.qsx.appoint.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.security.JwtTokenProvider;
import com.qsx.appoint.support.RegressionTest;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 独立H2验证实际关联、权限和密码说明，不访问常驻环境或读取本机密码。 */
@RegressionTest(properties = "appoint.local-environment.name=test")
@AutoConfigureMockMvc
@Transactional
class DoctorAccountQueryHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired SqlSessionTemplate sqlSession;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/admin/doctor-accounts")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "DOCTOR"})
    void nonAdminCannotReadAccounts(String role) throws Exception {
        mvc.perform(get("/api/v1/admin/doctor-accounts").header("Authorization", token(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void includesEveryHospitalAndDepartmentAndUnboundDoctorWithStringIds() throws Exception {
        JsonNode data = query();
        assertThat(data.path("environment").asText()).isEqualTo("test");
        JsonNode rows = data.path("items");
        assertThat(rows.size()).isEqualTo(18);
        var hospitals = new java.util.HashSet<String>();
        var departments = new java.util.HashSet<String>();
        for (JsonNode row : rows) {
            hospitals.add(row.path("hospitalId").asText());
            departments.add(row.path("departmentId").asText());
            for (String key : List.of("hospitalId", "departmentId", "doctorId"))
                assertThat(row.path(key).isTextual()).isTrue();
            assertThat(row.path("userId").isNull()).isTrue();
            assertThat(row.path("email").isNull()).isTrue();
            assertThat(row.path("bindingStatus").asText()).isEqualTo("UNBOUND");
            assertThat(row.path("passwordHint").asText()).isEqualTo("未绑定账号");
        }
        assertThat(hospitals).hasSize(2);
        assertThat(departments).hasSize(6);
        assertThat(rows.get(0).path("doctorName").asText()).isEqualTo("张医生");
        assertThat(rows.get(17).path("doctorName").asText()).isEqualTo("马医生");
    }

    @Test
    void usesSeedOwnershipInsteadOfEmailPatternAndNeverReturnsPasswordMaterial() throws Exception {
        long seeded = bind(1, "test-doctor-001@example.local");
        jdbc.update("INSERT INTO local_environment_seed_accounts(email,user_id,role_code) VALUES (?,?,?)",
                "test-doctor-001@example.local", seeded, "DOCTOR");
        // 故意使用预置邮箱格式，但没有初始化归属记录，必须按自行注册账号处理。
        bind(2, "test-doctor-002@example.local");
        JsonNode data = query();
        JsonNode first = findDoctor(data, "张医生");
        assertThat(first.path("userId").asText()).isEqualTo(String.valueOf(seeded));
        assertThat(first.path("bindingStatus").asText()).isEqualTo("BOUND");
        assertThat(first.path("passwordHint").asText()).isEqualTo("初始密码见 .env.test 的 LOCAL_SEED_PASSWORD");
        assertThat(findDoctor(data, "李医生").path("passwordHint").asText()).isEqualTo("使用注册时设置的密码");
        assertThat(data.toString()).doesNotContain("fixture-password-hash", "passwordHash", "password_hash", "\"password\"");
    }

    @Test
    void readsCurrentCatalogAndBindingsOnEveryRequest() throws Exception {
        assertThat(findDoctor(query(), "张医生").path("bindingStatus").asText()).isEqualTo("UNBOUND");
        bind(1, "registered-doctor@example.com");
        jdbc.update("UPDATE doctors SET name='维护后的医生', department_id=2 WHERE id=1");
        jdbc.update("UPDATE hospitals SET name='维护后的医院' WHERE id=1");
        JsonNode doctor = findDoctor(query(), "维护后的医生");
        assertThat(doctor.path("hospitalName").asText()).isEqualTo("维护后的医院");
        assertThat(doctor.path("departmentName").asText()).isEqualTo("骨科");
        assertThat(doctor.path("email").asText()).isEqualTo("registered-doctor@example.com");
        assertThat(doctor.path("bindingStatus").asText()).isEqualTo("BOUND");
    }

    @Test
    void emptyCatalogReturnsAnEmptyList() throws Exception {
        jdbc.update("DELETE FROM schedules");
        jdbc.update("DELETE FROM doctors");
        assertThat(query().path("items").size()).isZero();
    }

    private long bind(long doctorId, String email) {
        jdbc.update("INSERT INTO users(email,password_hash) VALUES (?,?)", email, "fixture-password-hash");
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        jdbc.update("UPDATE doctors SET user_id=? WHERE id=?", userId, doctorId);
        return userId;
    }

    private JsonNode query() throws Exception {
        // 测试外层事务跨请求共享SqlSession；清除直接JDBC造数前的一级缓存，模拟真实独立请求。
        sqlSession.clearCache();
        String body = mvc.perform(get("/api/v1/admin/doctor-accounts").header("Authorization", token("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(body).path("data");
    }

    private JsonNode findDoctor(JsonNode data, String name) {
        for (JsonNode row : data.path("items")) if (row.path("doctorName").asText().equals(name)) return row;
        throw new AssertionError("缺少医生：" + name);
    }

    private String token(String role) {
        return "Bearer " + tokens.createToken(999L, "fixture@example.com", List.of(role));
    }
}

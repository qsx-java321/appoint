package com.qsx.appoint.localenv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.support.RegressionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 独立H2与MockMvc验证目录、账号、角色、绑定及重复初始化，不读取本机配置。 */
@RegressionTest
@AutoConfigureMockMvc
@Transactional
class LocalEnvironmentAccountsHttpTest {
    private static final String PASSWORD = "fixture-password-123";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "demo"})
    void everyEnvironmentHasTwoHospitalsSixDepartmentsAndEighteenBoundDoctorAccounts(String environment) throws Exception {
        seed(environment, PASSWORD);
        assertThat(count("SELECT COUNT(*) FROM hospitals")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT COUNT(*) FROM departments GROUP BY hospital_id", Integer.class))
                .containsExactlyInAnyOrder(3, 3);
        assertThat(jdbc.queryForList("SELECT COUNT(*) FROM doctors GROUP BY department_id", Integer.class))
                .containsExactlyInAnyOrder(3, 3, 3, 3, 3, 3);
        assertThat(count("SELECT COUNT(*) FROM doctors d JOIN local_environment_seed_accounts s ON s.user_id=d.user_id WHERE s.role_code='DOCTOR'"))
                .isEqualTo(18);
        assertThat(count("SELECT COUNT(*) FROM local_environment_seed_accounts s JOIN user_roles ur ON ur.user_id=s.user_id JOIN roles r ON r.id=ur.role_id WHERE s.role_code='DOCTOR' AND r.code='DOCTOR'"))
                .isEqualTo(18);
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(environment.equals("demo") ? 20 : 19);

        String admin = login(environment + "-admin@example.local", PASSWORD, "ADMIN");
        mvc.perform(get("/api/v1/admin/booking-settings").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        String firstDoctor = login(environment + "-doctor-001@example.local", PASSWORD, "DOCTOR");
        String lastDoctor = login(environment + "-doctor-018@example.local", PASSWORD, "DOCTOR");
        for (String token : java.util.List.of(firstDoctor, lastDoctor)) {
            mvc.perform(get("/api/v1/doctor/sessions").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4));
        }
        mvc.perform(get("/api/v1/admin/booking-settings").header("Authorization", "Bearer " + firstDoctor))
                .andExpect(status().isForbidden());
        if (environment.equals("demo")) login("demo-user@example.local", PASSWORD, "USER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void restartingPreservesPasswordsBindingsAndManuallyMaintainedCatalog(String environment) throws Exception {
        seed(environment, PASSWORD);
        String email = environment + "-doctor-001@example.local";
        String changed = new BCryptPasswordEncoder().encode("changed-password-123");
        jdbc.update("UPDATE users SET password_hash=? WHERE email=?", changed, email);
        long binding = jdbc.queryForObject("SELECT user_id FROM doctors WHERE id=1", Long.class);
        jdbc.update("UPDATE doctors SET introduction='管理员已维护的介绍' WHERE id=1");
        seed(environment, "different-seed-password-123");
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email)).isEqualTo(changed);
        assertThat(jdbc.queryForObject("SELECT user_id FROM doctors WHERE id=1", Long.class)).isEqualTo(binding);
        assertThat(jdbc.queryForObject("SELECT introduction FROM doctors WHERE id=1", String.class)).isEqualTo("管理员已维护的介绍");
        login(email, "changed-password-123", "DOCTOR");
    }

    @Test
    void conflictingEmailDoesNotGrantRolesOrLeavePartiallyCreatedAccounts() {
        jdbc.update("INSERT INTO users(email,password_hash) VALUES (?,?)", "dev-doctor-018@example.local", "existing-hash");
        assertThatThrownBy(() -> seed("dev", PASSWORD)).isInstanceOf(IllegalStateException.class).hasMessageContaining("账号冲突");
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM local_environment_seed_accounts")).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_roles")).isZero();
        assertThat(count("SELECT COUNT(*) FROM doctors WHERE user_id IS NOT NULL")).isZero();
    }

    @Test
    void conflictingDoctorBindingIsPreservedAndInitializationRollsBack() {
        jdbc.update("INSERT INTO users(email,password_hash) VALUES ('existing-doctor@example.local','existing-hash')");
        long existing = jdbc.queryForObject("SELECT id FROM users WHERE email='existing-doctor@example.local'", Long.class);
        jdbc.update("UPDATE doctors SET user_id=? WHERE id=1", existing);
        assertThatThrownBy(() -> seed("test", PASSWORD)).isInstanceOf(IllegalStateException.class).hasMessageContaining("其他账号绑定");
        assertThat(jdbc.queryForObject("SELECT user_id FROM doctors WHERE id=1", Long.class)).isEqualTo(existing);
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM local_environment_seed_accounts")).isZero();
    }

    private void seed(String environment, String password) {
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        transaction.executeWithoutResult(status -> new LocalEnvironmentAccounts(jdbc).seed(environment, password));
    }

    private int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }

    private String login(String email, String password, String role) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.roles", hasItem(role)))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsString()).at("/data/accessToken").asText();
    }
}

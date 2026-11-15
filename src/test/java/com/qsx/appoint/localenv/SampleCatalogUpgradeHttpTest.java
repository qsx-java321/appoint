package com.qsx.appoint.localenv;

import com.qsx.appoint.support.RegressionTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 专用H2模拟已使用V1的库升级，验证新增样例不覆盖既有目录及编号。 */
@RegressionTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sample_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.flyway.target=1"
})
@AutoConfigureMockMvc
@DirtiesContext
class SampleCatalogUpgradeHttpTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;

    @Test
    void upgradesV1WithoutOverwritingExistingRecordsOrReusingAllocatedDoctorNumbers() throws Exception {
        jdbc.update("UPDATE doctors SET name='已维护的医生姓名' WHERE id=1");
        jdbc.update("INSERT INTO users(email,password_hash) VALUES ('existing@example.local','existing-hash')");
        jdbc.update("UPDATE doctors SET user_id=(SELECT id FROM users WHERE email='existing@example.local') WHERE id=1");
        jdbc.update("INSERT INTO doctor_number_allocations(allocation_token) VALUES ('MANUAL-DOCTOR')");
        jdbc.update("INSERT INTO doctors(doctor_no,department_id,name,title) VALUES ('DOC000003',1,'人工新增医生','主治医师')");

        Flyway upgrade = Flyway.configure().configuration(flyway.getConfiguration()).target("2").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctors", Integer.class)).isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT name FROM doctors WHERE id=1", String.class)).isEqualTo("已维护的医生姓名");
        assertThat(jdbc.queryForObject("SELECT u.email FROM doctors d JOIN users u ON u.id=d.user_id WHERE d.id=1", String.class))
                .isEqualTo("existing@example.local");
        assertThat(jdbc.queryForObject("SELECT name FROM doctors WHERE doctor_no='DOC000003'", String.class)).isEqualTo("人工新增医生");
        assertThat(jdbc.queryForObject("SELECT name FROM doctors WHERE doctor_no='DOC000004'", String.class)).isEqualTo("王医生");
        mvc.perform(get("/api/v1/hospitals").with(user("user").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/hospitals/1/departments").with(user("user").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(get("/api/v1/departments/1/doctors").with(user("user").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4));
    }
}

package com.qsx.appoint.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitSchemaMigrationTest {
    @Test
    void v9UpgradePreservesCatalogAndAddsEmptyVisitStorageAndDoctorRole() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:visit-migration-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure().dataSource(source).target("9").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var before = jdbc.queryForList("SELECT id, doctor_no, name FROM doctors ORDER BY id");
        var flyway = Flyway.configure().dataSource(source).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT id, doctor_no, name FROM doctors ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctors WHERE user_id IS NOT NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_records", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT code FROM roles", String.class)).containsExactlyInAnyOrder("USER", "ADMIN", "DOCTOR");
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(80001,'doctor-migration@example.com','hash')");
        jdbc.update("UPDATE doctors SET user_id=80001 WHERE id=1");
        assertThatThrownBy(() -> jdbc.update("UPDATE doctors SET user_id=80001 WHERE id=2")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE doctors SET user_id=999999 WHERE id=2")).isInstanceOf(Exception.class);
    }
}

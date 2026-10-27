package com.qsx.appoint.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InitialSchemaMigrationTest {
    @Test
    void emptyDatabaseIsInitializedOnceWithFinalSchemaAndSeedCatalog() {
        String databaseName = "initial-schema-" + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version = '1'",
                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hospitals", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctors", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT doctor_no FROM doctors WHERE id = 1", String.class))
                .isEqualTo("DOC000001");
        assertThat(jdbc.queryForObject("SELECT MAX(id) FROM doctor_number_allocations", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedules", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM patients", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointment_create_requests", Integer.class)).isZero();
    }
}

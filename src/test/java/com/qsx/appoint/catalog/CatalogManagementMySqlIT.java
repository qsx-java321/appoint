package com.qsx.appoint.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CatalogManagementMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appoint")
            .withUsername("appoint")
            .withPassword("appoint-test");

    @Test
    void v8EnforcesDepartmentNameUniquenessWithinHospital() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isPositive();
        assertThat(flyway.info().pending()).isEmpty();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO hospitals (id, name, address) VALUES (901, '甲医院', '甲地址')");
        jdbc.update("INSERT INTO hospitals (id, name, address) VALUES (902, '乙医院', '乙地址')");
        jdbc.update("INSERT INTO departments (hospital_id, name) VALUES (901, '心血管内科')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO departments (hospital_id, name) VALUES (901, '心血管内科')"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM departments WHERE hospital_id=901 AND name='心血管内科'", Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO departments (hospital_id, name) VALUES (902, '心血管内科')");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM departments WHERE name = '心血管内科'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'departments'
                  AND index_name = 'uk_departments_hospital_name'
                """, Integer.class)).isEqualTo(2);
    }
}

package com.qsx.appoint.mysql;

import com.qsx.appoint.support.MySqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

/** 每例使用全新MySQL，验证正式迁移链及已有数据升级。 */
@MySqlIntegrationTest(properties="spring.flyway.enabled=false")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MigrationMySqlIT {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    private Flyway migration(String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }
    @Test void initializesEmptyDatabaseAndMigratesOnlyOnce() throws Exception {
        try(var c=dataSource.getConnection()) { assertThat(c.getMetaData().getDatabaseProductName()).isEqualTo("MySQL"); }
        var flyway=migration("2");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'",Integer.class)).isEqualTo(16);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('payment_attempts','payment_refunds','payment_notifications','payment_outbox_events','coordination_inbox_events','schedule_inventory_adjustments')",Integer.class)).isZero();
        for(var entry:java.util.Map.of("hospitals",2,"departments",6,"doctors",18).entrySet())
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+entry.getKey(),Integer.class)).isEqualTo(entry.getValue());
        assertThat(jdbc.queryForObject("SELECT capacity_per_session FROM booking_settings",Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT registration_fee FROM booking_settings",java.math.BigDecimal.class)).isEqualByComparingTo("10.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0",Integer.class)).isZero();
    }
    @Test void upgradesV1PreservingManualRecordsAndBindings() {
        migration("1").migrate();
        jdbc.update("UPDATE doctors SET name='已维护姓名' WHERE id=1");
        jdbc.update("INSERT INTO users(email,password_hash) VALUES ('existing@example.local','existing-hash')");
        jdbc.update("UPDATE doctors SET user_id=(SELECT id FROM users WHERE email='existing@example.local') WHERE id=1");
        jdbc.update("INSERT INTO doctor_number_allocations(allocation_token) VALUES ('MANUAL-DOCTOR')");
        jdbc.update("INSERT INTO doctors(doctor_no,department_id,name,title) VALUES ('DOC000003',1,'人工医生','主治医师')");
        var upgrade=migration("2"); assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        upgrade.validate(); assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM doctors",Integer.class)).isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT name FROM doctors WHERE id=1",String.class)).isEqualTo("已维护姓名");
        assertThat(jdbc.queryForObject("SELECT u.email FROM users u JOIN doctors d ON d.user_id=u.id WHERE d.id=1",String.class)).isEqualTo("existing@example.local");
        assertThat(jdbc.queryForObject("SELECT name FROM doctors WHERE doctor_no='DOC000003'",String.class)).isEqualTo("人工医生");
        assertThat(jdbc.queryForObject("SELECT name FROM doctors WHERE doctor_no='DOC000004'",String.class)).isEqualTo("王医生");
    }
}

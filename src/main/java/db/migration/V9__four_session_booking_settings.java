package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Statement;

public class V9__four_session_booking_settings extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean mysql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE booking_settings (
                        id SMALLINT PRIMARY KEY,
                        capacity_per_session INT NOT NULL,
                        registration_fee DECIMAL(10,2) NOT NULL,
                        currency CHAR(3) NOT NULL,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT ck_booking_settings_singleton CHECK (id = 1),
                        CONSTRAINT ck_booking_settings_capacity CHECK (capacity_per_session > 0),
                        CONSTRAINT ck_booking_settings_fee CHECK (registration_fee > 0 AND currency = 'CNY')
                    )
                    """);
            statement.execute("""
                    INSERT INTO booking_settings (id, capacity_per_session, registration_fee, currency)
                    VALUES (1, 20, 10.00, 'CNY')
                    """);
            statement.execute(mysql
                    ? "ALTER TABLE schedules DROP CHECK ck_schedules_session"
                    : "ALTER TABLE schedules DROP CONSTRAINT ck_schedules_session");
            statement.execute(mysql
                    ? "ALTER TABLE schedules DROP CHECK ck_schedules_fixed_times"
                    : "ALTER TABLE schedules DROP CONSTRAINT ck_schedules_fixed_times");
            statement.execute("""
                    ALTER TABLE schedules ADD CONSTRAINT ck_schedules_session CHECK (
                        session_type IS NULL OR session_type IN (
                            'MORNING', 'AFTERNOON',
                            'MORNING_FIRST', 'MORNING_SECOND',
                            'AFTERNOON_FIRST', 'AFTERNOON_SECOND'))
                    """);
            statement.execute("""
                    ALTER TABLE schedules ADD CONSTRAINT ck_schedules_fixed_times CHECK (
                        session_type IS NULL
                        OR (session_type = 'MORNING' AND start_time = '08:00:00' AND end_time = '12:00:00')
                        OR (session_type = 'AFTERNOON' AND start_time = '14:00:00' AND end_time = '17:00:00')
                        OR (session_type = 'MORNING_FIRST' AND start_time = '08:00:00' AND end_time = '10:00:00')
                        OR (session_type = 'MORNING_SECOND' AND start_time = '10:30:00' AND end_time = '12:00:00')
                        OR (session_type = 'AFTERNOON_FIRST' AND start_time = '14:00:00' AND end_time = '16:00:00')
                        OR (session_type = 'AFTERNOON_SECOND' AND start_time = '16:30:00' AND end_time = '18:00:00'))
                    """);
        }
    }
}

ALTER TABLE doctors ADD COLUMN user_id BIGINT;
ALTER TABLE doctors ADD CONSTRAINT uk_doctors_user UNIQUE (user_id);
ALTER TABLE doctors ADD CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id);

INSERT INTO roles (code, name) VALUES ('DOCTOR', '医生');

CREATE TABLE visit_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    checked_in_at DATETIME NOT NULL,
    started_at DATETIME,
    completed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_visit_records_appointment UNIQUE (appointment_order_id),
    CONSTRAINT fk_visit_records_appointment FOREIGN KEY (appointment_order_id) REFERENCES appointment_orders (id),
    CONSTRAINT ck_visit_records_status CHECK (status IN ('WAITING', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_appointments_user_visit_time ON appointment_orders (user_id, appointment_date, start_time, id);
CREATE INDEX idx_appointments_doctor_visit_date ON appointment_orders (doctor_id, appointment_date, status, schedule_id, visit_number);

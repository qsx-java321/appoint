ALTER TABLE schedules ADD COLUMN session_type VARCHAR(16);
ALTER TABLE schedules ADD COLUMN total_capacity INT;
ALTER TABLE schedules ADD COLUMN available_capacity INT;
ALTER TABLE schedules ADD COLUMN held_capacity INT;
ALTER TABLE schedules ADD COLUMN confirmed_capacity INT;
ALTER TABLE schedules ADD COLUMN next_visit_number INT;
ALTER TABLE schedules ADD COLUMN closed_at DATETIME;
ALTER TABLE schedules ADD COLUMN closed_by BIGINT;

ALTER TABLE schedules ADD CONSTRAINT uk_schedules_doctor_date_session
    UNIQUE (doctor_id, appointment_date, session_type);
ALTER TABLE schedules ADD CONSTRAINT fk_schedules_closed_by
    FOREIGN KEY (closed_by) REFERENCES users (id);
ALTER TABLE schedules ADD CONSTRAINT ck_schedules_session
    CHECK (session_type IS NULL OR session_type IN ('MORNING', 'AFTERNOON'));
ALTER TABLE schedules ADD CONSTRAINT ck_schedules_fixed_times
    CHECK (session_type IS NULL
        OR (session_type = 'MORNING' AND start_time = '08:00:00' AND end_time = '12:00:00')
        OR (session_type = 'AFTERNOON' AND start_time = '14:00:00' AND end_time = '17:00:00'));
ALTER TABLE schedules ADD CONSTRAINT ck_schedules_inventory
    CHECK (session_type IS NULL OR (
        total_capacity >= 0
        AND available_capacity >= 0
        AND held_capacity >= 0
        AND confirmed_capacity >= 0
        AND total_capacity = available_capacity + held_capacity + confirmed_capacity
        AND next_visit_number >= 1));

ALTER TABLE appointment_orders ADD COLUMN visit_number INT;

CREATE TABLE appointment_active_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    appointment_order_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    document_no VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_active_claim_order UNIQUE (appointment_order_id),
    CONSTRAINT uk_active_claim_patient UNIQUE (schedule_id, patient_id),
    CONSTRAINT uk_active_claim_document UNIQUE (schedule_id, document_type, document_no),
    CONSTRAINT fk_active_claim_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
    CONSTRAINT fk_active_claim_order FOREIGN KEY (appointment_order_id) REFERENCES appointment_orders (id),
    CONSTRAINT fk_active_claim_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
);

CREATE TABLE schedule_inventory_adjustments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    total_capacity_after INT NOT NULL,
    available_capacity_after INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_adjustment_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
    CONSTRAINT fk_inventory_adjustment_operator FOREIGN KEY (operator_user_id) REFERENCES users (id),
    CONSTRAINT ck_inventory_adjustment_delta CHECK (delta <> 0)
);

CREATE INDEX idx_new_schedules_doctor_date ON schedules (doctor_id, appointment_date, session_type);
CREATE INDEX idx_active_claim_schedule ON appointment_active_claims (schedule_id);
CREATE INDEX idx_inventory_adjustment_schedule ON schedule_inventory_adjustments (schedule_id, id);

INSERT INTO schedules (
    doctor_id, appointment_date, start_time, end_time, status, session_type,
    total_capacity, available_capacity, held_capacity, confirmed_capacity, next_visit_number)
VALUES (1, '2099-01-01', '08:00:00', '12:00:00', 'OPEN', 'MORNING', 20, 20, 0, 0, 1);

INSERT INTO schedules (
    doctor_id, appointment_date, start_time, end_time, status, session_type,
    total_capacity, available_capacity, held_capacity, confirmed_capacity, next_visit_number)
VALUES (2, '2099-01-02', '14:00:00', '17:00:00', 'OPEN', 'AFTERNOON', 20, 20, 0, 0, 1);

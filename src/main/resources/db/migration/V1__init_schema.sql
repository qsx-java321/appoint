-- 医院预约系统首版完整结构与基础数据，仅用于空库初始化。
-- 已合并原 V1 至 V11 的 SQL/Java 迁移结果；不执行旧库升级或清理。
-- 环境账号及密码由应用的本地环境生命周期管理，不在此脚本写入。

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE hospitals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(200) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_departments_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id),
    CONSTRAINT uk_departments_hospital_name UNIQUE (hospital_id, name)
);

CREATE TABLE doctor_number_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    allocation_token VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doctor_number_allocations_token UNIQUE (allocation_token)
);

CREATE TABLE doctors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_no VARCHAR(32) NOT NULL,
    department_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    title VARCHAR(64) NOT NULL,
    introduction VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT,
    CONSTRAINT uk_doctors_doctor_no UNIQUE (doctor_no),
    CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT uk_doctors_user UNIQUE (user_id),
    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    session_type VARCHAR(16),
    total_capacity INT,
    available_capacity INT,
    held_capacity INT,
    confirmed_capacity INT,
    next_visit_number INT,
    closed_at DATETIME,
    closed_by BIGINT,
    registration_fee DECIMAL(10,2),
    fee_currency CHAR(3),
    CONSTRAINT fk_schedules_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT uk_schedules_doctor_date_session UNIQUE (doctor_id, appointment_date, session_type),
    CONSTRAINT fk_schedules_closed_by FOREIGN KEY (closed_by) REFERENCES users (id),
    CONSTRAINT ck_schedules_session CHECK (
        session_type IS NULL OR session_type IN (
            'MORNING', 'AFTERNOON',
            'MORNING_FIRST', 'MORNING_SECOND',
            'AFTERNOON_FIRST', 'AFTERNOON_SECOND')),
    CONSTRAINT ck_schedules_fixed_times CHECK (
        session_type IS NULL
        OR (session_type = 'MORNING' AND start_time = '08:00:00' AND end_time = '12:00:00')
        OR (session_type = 'AFTERNOON' AND start_time = '14:00:00' AND end_time = '17:00:00')
        OR (session_type = 'MORNING_FIRST' AND start_time = '08:00:00' AND end_time = '10:00:00')
        OR (session_type = 'MORNING_SECOND' AND start_time = '10:30:00' AND end_time = '12:00:00')
        OR (session_type = 'AFTERNOON_FIRST' AND start_time = '14:00:00' AND end_time = '16:00:00')
        OR (session_type = 'AFTERNOON_SECOND' AND start_time = '16:30:00' AND end_time = '18:00:00')),
    CONSTRAINT ck_schedules_inventory CHECK (session_type IS NULL OR (
        total_capacity >= 0
        AND available_capacity >= 0
        AND held_capacity >= 0
        AND confirmed_capacity >= 0
        AND total_capacity = available_capacity + held_capacity + confirmed_capacity
        AND next_visit_number >= 1)),
    CONSTRAINT ck_schedules_fee CHECK ((registration_fee IS NULL AND fee_currency IS NULL)
        OR (registration_fee IS NOT NULL AND fee_currency IS NOT NULL
            AND registration_fee > 0 AND fee_currency = 'CNY'))
);

CREATE INDEX idx_schedules_doctor_status ON schedules (doctor_id, status);

CREATE INDEX idx_new_schedules_doctor_date ON schedules (doctor_id, appointment_date, session_type);

CREATE TABLE patients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    document_no VARCHAR(32) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    contact_email VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_patients_user_document UNIQUE (user_id, document_type, document_no),
    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_patients_user_id ON patients (user_id, id);

CREATE TABLE appointment_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    patient_name VARCHAR(64) NOT NULL,
    patient_document_type VARCHAR(32) NOT NULL,
    patient_document_no VARCHAR(32) NOT NULL,
    patient_contact_phone VARCHAR(32) NOT NULL,
    patient_contact_email VARCHAR(120) NOT NULL,
    doctor_id BIGINT NOT NULL,
    doctor_no VARCHAR(32) NOT NULL,
    doctor_name VARCHAR(64) NOT NULL,
    doctor_title VARCHAR(64) NOT NULL,
    department_id BIGINT NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    hospital_id BIGINT NOT NULL,
    hospital_name VARCHAR(100) NOT NULL,
    hospital_address VARCHAR(200) NOT NULL,
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_expires_at DATETIME NOT NULL,
    closed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    visit_number INT,
    fee_amount DECIMAL(10,2),
    fee_currency CHAR(3),
    CONSTRAINT uk_appointment_orders_no UNIQUE (appointment_no),
    CONSTRAINT fk_appointment_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_appointment_orders_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
    CONSTRAINT fk_appointment_orders_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT ck_appointment_orders_fee CHECK ((fee_amount IS NULL AND fee_currency IS NULL)
        OR (fee_amount IS NOT NULL AND fee_currency IS NOT NULL
            AND fee_amount > 0 AND fee_currency = 'CNY'))
);

CREATE INDEX idx_appointment_orders_user_id ON appointment_orders (user_id, id);

CREATE INDEX idx_appointment_expiration ON appointment_orders (status, payment_expires_at, id);

CREATE INDEX idx_appointments_user_visit_time ON appointment_orders (user_id, appointment_date, start_time, id);

CREATE INDEX idx_appointments_doctor_visit_date ON appointment_orders (doctor_id, appointment_date, status, schedule_id, visit_number);

CREATE TABLE payment_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL,
    appointment_order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    amount DECIMAL(10,2),
    currency CHAR(3),
    expires_at DATETIME,
    paid_at DATETIME,
    closed_at DATETIME,
    refunded_at DATETIME,
    legacy BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_payment_orders_no UNIQUE (payment_no),
    CONSTRAINT uk_payment_orders_appointment UNIQUE (appointment_order_id),
    CONSTRAINT fk_payment_orders_appointment FOREIGN KEY (appointment_order_id) REFERENCES appointment_orders (id),
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_payment_orders_status CHECK (status IN ('UNPAID', 'PAYING', 'PAID', 'CLOSED', 'REFUND_PENDING', 'REFUNDED')),
    CONSTRAINT ck_payment_orders_new_fields CHECK ((legacy = TRUE)
        OR (legacy = FALSE AND amount IS NOT NULL AND currency IS NOT NULL
            AND amount > 0 AND currency = 'CNY' AND expires_at IS NOT NULL))
);

CREATE TABLE appointment_create_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    appointment_order_id BIGINT,
    response_json TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_appointment_create_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_appointment_create_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_appointment_create_order FOREIGN KEY (appointment_order_id) REFERENCES appointment_orders (id)
);

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

CREATE INDEX idx_active_claim_schedule ON appointment_active_claims (schedule_id);

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

CREATE INDEX idx_inventory_adjustment_schedule ON schedule_inventory_adjustments (schedule_id, id);

CREATE TABLE payment_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_no VARCHAR(64) NOT NULL,
    payment_order_id BIGINT NOT NULL,
    channel VARCHAR(32) NOT NULL,
    provider_order_no VARCHAR(128),
    provider_transaction_no VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64),
    failure_message VARCHAR(255),
    created_at DATETIME NOT NULL,
    processing_at DATETIME,
    completed_at DATETIME,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_payment_attempts_no UNIQUE (attempt_no),
    CONSTRAINT uk_payment_attempts_provider_order UNIQUE (channel, provider_order_no),
    CONSTRAINT uk_payment_attempts_provider_transaction UNIQUE (channel, provider_transaction_no),
    CONSTRAINT fk_payment_attempts_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    CONSTRAINT ck_payment_attempts_status CHECK (status IN ('CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CLOSED'))
);

CREATE INDEX idx_payment_attempts_order_status ON payment_attempts (payment_order_id, status);

CREATE TABLE payment_refunds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL,
    payment_order_id BIGINT NOT NULL,
    payment_attempt_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    provider_refund_no VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    attempt_count INT NOT NULL,
    last_failure_code VARCHAR(64),
    last_failure_message VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    completed_at DATETIME,
    CONSTRAINT uk_payment_refunds_no UNIQUE (refund_no),
    CONSTRAINT uk_payment_refunds_order UNIQUE (payment_order_id),
    CONSTRAINT uk_payment_refunds_provider UNIQUE (channel, provider_refund_no),
    CONSTRAINT fk_payment_refunds_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    CONSTRAINT fk_payment_refunds_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts (id),
    CONSTRAINT ck_payment_refunds_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_payment_refunds_amount CHECK (amount > 0 AND currency = 'CNY' AND attempt_count >= 0)
);

CREATE INDEX idx_payment_refunds_status_updated ON payment_refunds (status, updated_at, id);

CREATE TABLE payment_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel VARCHAR(32) NOT NULL,
    notification_id VARCHAR(128) NOT NULL,
    provider_transaction_no VARCHAR(128),
    payload_hash VARCHAR(64) NOT NULL,
    verification_result VARCHAR(32) NOT NULL,
    processing_result VARCHAR(32),
    received_at DATETIME NOT NULL,
    processed_at DATETIME,
    CONSTRAINT uk_payment_notifications_channel_id UNIQUE (channel, notification_id)
);

CREATE INDEX idx_payment_notifications_transaction ON payment_notifications (channel, provider_transaction_no);

CREATE TABLE payment_outbox_events (
    event_id CHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INT NOT NULL,
    payload JSON NOT NULL,
    occurred_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    publish_attempts INT NOT NULL,
    next_attempt_at DATETIME NOT NULL,
    published_at DATETIME,
    last_error VARCHAR(500),
    CONSTRAINT ck_payment_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_payment_outbox_counters CHECK (event_version > 0 AND publish_attempts >= 0)
);

CREATE INDEX idx_payment_outbox_scan
    ON payment_outbox_events (status, next_attempt_at, occurred_at, event_id);

CREATE TABLE coordination_inbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(128) NOT NULL,
    event_id CHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    processed_at DATETIME NOT NULL,
    CONSTRAINT uk_coordination_inbox_consumer_event UNIQUE (consumer_name, event_id)
);

CREATE TABLE booking_settings (
    id SMALLINT PRIMARY KEY,
    capacity_per_session INT NOT NULL,
    registration_fee DECIMAL(10,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_booking_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_booking_settings_capacity CHECK (capacity_per_session > 0),
    CONSTRAINT ck_booking_settings_fee CHECK (registration_fee > 0 AND currency = 'CNY')
);

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

CREATE TABLE local_environment_seed_accounts (
    email VARCHAR(120) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    CONSTRAINT fk_local_seed_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 基础角色、医疗目录、医生编号分配及历史示例场次；保持原迁移链的初始数据。
INSERT INTO roles (code, name) VALUES ('USER', '普通用户');

INSERT INTO roles (code, name) VALUES ('ADMIN', '管理员');

INSERT INTO hospitals (name, address) VALUES ('示例市第一人民医院', '示例市健康路 1 号');

INSERT INTO departments (hospital_id, name) VALUES (1, '内科');

INSERT INTO departments (hospital_id, name) VALUES (1, '骨科');

INSERT INTO doctor_number_allocations (allocation_token) VALUES ('SEED-DOCTOR-1');

INSERT INTO doctor_number_allocations (allocation_token) VALUES ('SEED-DOCTOR-2');

INSERT INTO doctors (doctor_no, department_id, name, title, introduction)
VALUES ('DOC000001', 1, '张医生', '主任医师', '擅长常见内科疾病诊治。');

INSERT INTO doctors (doctor_no, department_id, name, title, introduction)
VALUES ('DOC000002', 2, '李医生', '副主任医师', '擅长骨科常见疾病诊治。');

INSERT INTO schedules (doctor_id, appointment_date, start_time, end_time, status)
VALUES (1, '2099-01-01', '09:00:00', '09:30:00', 'AVAILABLE');

INSERT INTO schedules (doctor_id, appointment_date, start_time, end_time, status)
VALUES (1, '2099-01-01', '09:30:00', '10:00:00', 'AVAILABLE');

INSERT INTO schedules (doctor_id, appointment_date, start_time, end_time, status)
VALUES (2, '2099-01-02', '14:00:00', '14:30:00', 'AVAILABLE');

INSERT INTO schedules (
    doctor_id, appointment_date, start_time, end_time, status, session_type,
    total_capacity, available_capacity, held_capacity, confirmed_capacity, next_visit_number)
VALUES (1, '2099-01-01', '08:00:00', '12:00:00', 'OPEN', 'MORNING', 20, 20, 0, 0, 1);

INSERT INTO schedules (
    doctor_id, appointment_date, start_time, end_time, status, session_type,
    total_capacity, available_capacity, held_capacity, confirmed_capacity, next_visit_number)
VALUES (2, '2099-01-02', '14:00:00', '17:00:00', 'OPEN', 'AFTERNOON', 20, 20, 0, 0, 1);

INSERT INTO booking_settings (id, capacity_per_session, registration_fee, currency)
VALUES (1, 20, 10.00, 'CNY');

INSERT INTO roles (code, name) VALUES ('DOCTOR', '医生');

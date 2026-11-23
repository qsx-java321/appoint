-- 医院预约系统当前基线，仅用于空库初始化。
-- 已移除已退出的管理员手工排班、支付尝试/退款/通知和消息事件结构。
-- 修改本基线后，已有 dev/test 库需执行 reset 重建；demo 每次启动自动重建。
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

-- 场次只由首次下单按统一设置生成，四种固定时段之外的状态不再存在。
CREATE TABLE schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    session_type VARCHAR(16) NOT NULL,
    total_capacity INT NOT NULL,
    available_capacity INT NOT NULL,
    held_capacity INT NOT NULL,
    confirmed_capacity INT NOT NULL,
    next_visit_number INT NOT NULL,
    registration_fee DECIMAL(10,2) NOT NULL,
    fee_currency CHAR(3) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_schedules_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT uk_schedules_doctor_date_session UNIQUE (doctor_id, appointment_date, session_type),
    CONSTRAINT ck_schedules_session CHECK (session_type IN (
        'MORNING_FIRST', 'MORNING_SECOND', 'AFTERNOON_FIRST', 'AFTERNOON_SECOND')),
    CONSTRAINT ck_schedules_fixed_times CHECK (
        (session_type = 'MORNING_FIRST' AND start_time = '08:00:00' AND end_time = '10:00:00')
        OR (session_type = 'MORNING_SECOND' AND start_time = '10:30:00' AND end_time = '12:00:00')
        OR (session_type = 'AFTERNOON_FIRST' AND start_time = '14:00:00' AND end_time = '16:00:00')
        OR (session_type = 'AFTERNOON_SECOND' AND start_time = '16:30:00' AND end_time = '18:00:00')),
    CONSTRAINT ck_schedules_inventory CHECK (
        total_capacity > 0
        AND available_capacity >= 0
        AND held_capacity >= 0
        AND confirmed_capacity >= 0
        AND total_capacity = available_capacity + held_capacity + confirmed_capacity
        AND next_visit_number >= 1),
    CONSTRAINT ck_schedules_fee CHECK (registration_fee > 0 AND fee_currency = 'CNY')
);

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
    fee_amount DECIMAL(10,2) NOT NULL,
    fee_currency CHAR(3) NOT NULL,
    CONSTRAINT uk_appointment_orders_no UNIQUE (appointment_no),
    CONSTRAINT fk_appointment_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_appointment_orders_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
    CONSTRAINT fk_appointment_orders_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT ck_appointment_orders_status CHECK (status IN (
        'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_appointment_orders_visit_number CHECK (visit_number IS NULL OR visit_number >= 1),
    CONSTRAINT ck_appointment_orders_fee CHECK (fee_amount > 0 AND fee_currency = 'CNY')
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
    amount DECIMAL(10,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    expires_at DATETIME NOT NULL,
    paid_at DATETIME,
    closed_at DATETIME,
    CONSTRAINT uk_payment_orders_no UNIQUE (payment_no),
    CONSTRAINT uk_payment_orders_appointment UNIQUE (appointment_order_id),
    CONSTRAINT fk_payment_orders_appointment FOREIGN KEY (appointment_order_id) REFERENCES appointment_orders (id),
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_payment_orders_status CHECK (status IN ('UNPAID', 'PAID', 'CLOSED')),
    CONSTRAINT ck_payment_orders_fields CHECK (amount > 0 AND currency = 'CNY')
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

-- 基础角色、医疗目录和医生编号分配；场次由下单时按需生成。
INSERT INTO roles (code, name) VALUES ('USER', '普通用户');

INSERT INTO roles (code, name) VALUES ('ADMIN', '管理员');

INSERT INTO roles (code, name) VALUES ('DOCTOR', '医生');

INSERT INTO hospitals (name, address) VALUES ('示例市第一人民医院', '示例市健康路 1 号');

INSERT INTO departments (hospital_id, name) VALUES (1, '内科');

INSERT INTO departments (hospital_id, name) VALUES (1, '骨科');

INSERT INTO doctor_number_allocations (allocation_token) VALUES ('SEED-DOCTOR-1');

INSERT INTO doctor_number_allocations (allocation_token) VALUES ('SEED-DOCTOR-2');

INSERT INTO doctors (doctor_no, department_id, name, title, introduction)
VALUES ('DOC000001', 1, '张医生', '主任医师', '擅长常见内科疾病诊治。');

INSERT INTO doctors (doctor_no, department_id, name, title, introduction)
VALUES ('DOC000002', 2, '李医生', '副主任医师', '擅长骨科常见疾病诊治。');

INSERT INTO booking_settings (id, capacity_per_session, registration_fee, currency)
VALUES (1, 20, 10.00, 'CNY');

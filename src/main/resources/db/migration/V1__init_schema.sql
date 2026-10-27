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
    CONSTRAINT fk_departments_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id)
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
    CONSTRAINT uk_doctors_doctor_no UNIQUE (doctor_no),
    CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments (id)
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
    CONSTRAINT fk_schedules_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id)
);

CREATE INDEX idx_schedules_doctor_status ON schedules (doctor_id, status);

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
    CONSTRAINT uk_appointment_orders_no UNIQUE (appointment_no),
    CONSTRAINT fk_appointment_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_appointment_orders_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
    CONSTRAINT fk_appointment_orders_patient FOREIGN KEY (patient_id) REFERENCES patients (id)
);

CREATE INDEX idx_appointment_orders_user_id ON appointment_orders (user_id, id);
CREATE INDEX idx_appointment_expiration ON appointment_orders (status, payment_expires_at, id);

CREATE TABLE payment_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL,
    appointment_order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_orders_no UNIQUE (payment_no),
    CONSTRAINT uk_payment_orders_appointment UNIQUE (appointment_order_id),
    CONSTRAINT fk_payment_orders_appointment FOREIGN KEY (appointment_order_id) REFERENCES appointment_orders (id),
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
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

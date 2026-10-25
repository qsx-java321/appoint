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

CREATE TABLE doctors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    title VARCHAR(64) NOT NULL,
    introduction VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
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

CREATE TABLE appointment_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appointment_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_appointment_orders_no UNIQUE (appointment_no),
    CONSTRAINT fk_appointment_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_appointment_orders_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id)
);

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

INSERT INTO hospitals (name, address) VALUES ('示例市第一人民医院', '示例市健康路 1 号');
INSERT INTO departments (hospital_id, name) VALUES (1, '内科');
INSERT INTO departments (hospital_id, name) VALUES (1, '骨科');
INSERT INTO doctors (department_id, name, title, introduction) VALUES (1, '张医生', '主任医师', '擅长常见内科疾病诊治。');
INSERT INTO doctors (department_id, name, title, introduction) VALUES (2, '李医生', '副主任医师', '擅长骨科常见疾病诊治。');
-- 使用足够靠后的固定日期，使同一份迁移可同时运行在 MySQL 与 H2 测试环境中。
INSERT INTO schedules (doctor_id, appointment_date, start_time, end_time, status) VALUES (1, '2099-01-01', '09:00:00', '09:30:00', 'AVAILABLE');
INSERT INTO schedules (doctor_id, appointment_date, start_time, end_time, status) VALUES (1, '2099-01-01', '09:30:00', '10:00:00', 'AVAILABLE');
INSERT INTO schedules (doctor_id, appointment_date, start_time, end_time, status) VALUES (2, '2099-01-02', '14:00:00', '14:30:00', 'AVAILABLE');


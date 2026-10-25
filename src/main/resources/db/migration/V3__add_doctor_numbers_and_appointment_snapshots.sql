CREATE TABLE doctor_number_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    allocation_token VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doctor_number_allocations_token UNIQUE (allocation_token)
);

ALTER TABLE doctors ADD COLUMN doctor_no VARCHAR(32);

INSERT INTO doctor_number_allocations (allocation_token)
SELECT CONCAT('MIGRATION-DOCTOR-', id)
FROM doctors
ORDER BY id;

UPDATE doctors d
SET doctor_no = (
    SELECT CONCAT('DOC', LPAD(a.id, 6, '0'))
    FROM doctor_number_allocations a
    WHERE a.allocation_token = CONCAT('MIGRATION-DOCTOR-', d.id)
);

ALTER TABLE doctors MODIFY COLUMN doctor_no VARCHAR(32) NOT NULL;
ALTER TABLE doctors ADD CONSTRAINT uk_doctors_doctor_no UNIQUE (doctor_no);

ALTER TABLE appointment_orders ADD COLUMN doctor_id BIGINT;
ALTER TABLE appointment_orders ADD COLUMN doctor_no VARCHAR(32);
ALTER TABLE appointment_orders ADD COLUMN doctor_name VARCHAR(64);
ALTER TABLE appointment_orders ADD COLUMN doctor_title VARCHAR(64);
ALTER TABLE appointment_orders ADD COLUMN department_id BIGINT;
ALTER TABLE appointment_orders ADD COLUMN department_name VARCHAR(100);
ALTER TABLE appointment_orders ADD COLUMN hospital_id BIGINT;
ALTER TABLE appointment_orders ADD COLUMN hospital_name VARCHAR(100);

UPDATE appointment_orders a
SET doctor_id = (
    SELECT d.id
    FROM schedules s
    JOIN doctors d ON d.id = s.doctor_id
    WHERE s.id = a.schedule_id
),
doctor_no = (
    SELECT d.doctor_no
    FROM schedules s
    JOIN doctors d ON d.id = s.doctor_id
    WHERE s.id = a.schedule_id
),
doctor_name = (
    SELECT d.name
    FROM schedules s
    JOIN doctors d ON d.id = s.doctor_id
    WHERE s.id = a.schedule_id
),
doctor_title = (
    SELECT d.title
    FROM schedules s
    JOIN doctors d ON d.id = s.doctor_id
    WHERE s.id = a.schedule_id
),
department_id = (
    SELECT dep.id
    FROM schedules s
    JOIN doctors d ON d.id = s.doctor_id
    JOIN departments dep ON dep.id = d.department_id
    WHERE s.id = a.schedule_id
),
department_name = (
    SELECT dep.name
    FROM schedules s
    JOIN doctors d ON d.id = s.doctor_id
    JOIN departments dep ON dep.id = d.department_id
    WHERE s.id = a.schedule_id
),
hospital_id = (
    SELECT h.id
    FROM schedules s
    JOIN doctors d ON d.id = s.doctor_id
    JOIN departments dep ON dep.id = d.department_id
    JOIN hospitals h ON h.id = dep.hospital_id
    WHERE s.id = a.schedule_id
),
hospital_name = (
    SELECT h.name
    FROM schedules s
    JOIN doctors d ON d.id = s.doctor_id
    JOIN departments dep ON dep.id = d.department_id
    JOIN hospitals h ON h.id = dep.hospital_id
    WHERE s.id = a.schedule_id
);

ALTER TABLE appointment_orders MODIFY COLUMN doctor_id BIGINT NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN doctor_no VARCHAR(32) NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN doctor_name VARCHAR(64) NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN doctor_title VARCHAR(64) NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN department_id BIGINT NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN department_name VARCHAR(100) NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN hospital_id BIGINT NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN hospital_name VARCHAR(100) NOT NULL;

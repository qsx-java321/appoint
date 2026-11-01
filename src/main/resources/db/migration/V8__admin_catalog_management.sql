ALTER TABLE departments ADD CONSTRAINT uk_departments_hospital_name
    UNIQUE (hospital_id, name);

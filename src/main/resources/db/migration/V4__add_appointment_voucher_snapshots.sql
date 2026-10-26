ALTER TABLE appointment_orders ADD COLUMN appointment_date DATE;
ALTER TABLE appointment_orders ADD COLUMN start_time TIME;
ALTER TABLE appointment_orders ADD COLUMN end_time TIME;
ALTER TABLE appointment_orders ADD COLUMN hospital_address VARCHAR(200);

UPDATE appointment_orders a
SET appointment_date = (SELECT s.appointment_date FROM schedules s WHERE s.id = a.schedule_id),
    start_time = (SELECT s.start_time FROM schedules s WHERE s.id = a.schedule_id),
    end_time = (SELECT s.end_time FROM schedules s WHERE s.id = a.schedule_id),
    hospital_address = (SELECT h.address FROM hospitals h WHERE h.id = a.hospital_id);

ALTER TABLE appointment_orders MODIFY COLUMN appointment_date DATE NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN start_time TIME NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN end_time TIME NOT NULL;
ALTER TABLE appointment_orders MODIFY COLUMN hospital_address VARCHAR(200) NOT NULL;

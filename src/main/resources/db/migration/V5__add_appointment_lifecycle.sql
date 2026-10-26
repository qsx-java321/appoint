ALTER TABLE appointment_orders ADD COLUMN payment_expires_at DATETIME;
ALTER TABLE appointment_orders ADD COLUMN closed_at DATETIME;

UPDATE appointment_orders
SET payment_expires_at = LEAST(
        TIMESTAMPADD(MINUTE, 15, created_at),
        CAST(CONCAT(appointment_date, ' ', start_time) AS DATETIME)
    );

ALTER TABLE appointment_orders MODIFY COLUMN payment_expires_at DATETIME NOT NULL;
CREATE INDEX idx_appointment_expiration
    ON appointment_orders (status, payment_expires_at, id);

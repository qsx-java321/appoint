ALTER TABLE schedules ADD COLUMN registration_fee DECIMAL(10,2);
ALTER TABLE schedules ADD COLUMN fee_currency CHAR(3);

ALTER TABLE appointment_orders ADD COLUMN fee_amount DECIMAL(10,2);
ALTER TABLE appointment_orders ADD COLUMN fee_currency CHAR(3);

ALTER TABLE payment_orders ADD COLUMN amount DECIMAL(10,2);
ALTER TABLE payment_orders ADD COLUMN currency CHAR(3);
ALTER TABLE payment_orders ADD COLUMN expires_at DATETIME;
ALTER TABLE payment_orders ADD COLUMN paid_at DATETIME;
ALTER TABLE payment_orders ADD COLUMN closed_at DATETIME;
ALTER TABLE payment_orders ADD COLUMN refunded_at DATETIME;
ALTER TABLE payment_orders ADD COLUMN legacy BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE payment_orders SET legacy = TRUE;

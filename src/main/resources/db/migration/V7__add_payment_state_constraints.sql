ALTER TABLE schedules ADD CONSTRAINT ck_schedules_fee
    CHECK ((registration_fee IS NULL AND fee_currency IS NULL)
        OR (registration_fee IS NOT NULL AND fee_currency IS NOT NULL
            AND registration_fee > 0 AND fee_currency = 'CNY'));

ALTER TABLE appointment_orders ADD CONSTRAINT ck_appointment_orders_fee
    CHECK ((fee_amount IS NULL AND fee_currency IS NULL)
        OR (fee_amount IS NOT NULL AND fee_currency IS NOT NULL
            AND fee_amount > 0 AND fee_currency = 'CNY'));

ALTER TABLE payment_orders ADD CONSTRAINT ck_payment_orders_status
    CHECK (status IN ('UNPAID', 'PAYING', 'PAID', 'CLOSED', 'REFUND_PENDING', 'REFUNDED'));
ALTER TABLE payment_orders ADD CONSTRAINT ck_payment_orders_new_fields
    CHECK ((legacy = TRUE)
        OR (legacy = FALSE AND amount IS NOT NULL AND currency IS NOT NULL
            AND amount > 0 AND currency = 'CNY' AND expires_at IS NOT NULL));

ALTER TABLE payment_attempts ADD CONSTRAINT ck_payment_attempts_status
    CHECK (status IN ('CREATED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CLOSED'));

ALTER TABLE payment_refunds ADD CONSTRAINT ck_payment_refunds_status
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'));
ALTER TABLE payment_refunds ADD CONSTRAINT ck_payment_refunds_amount
    CHECK (amount > 0 AND currency = 'CNY' AND attempt_count >= 0);

ALTER TABLE payment_outbox_events ADD CONSTRAINT ck_payment_outbox_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));
ALTER TABLE payment_outbox_events ADD CONSTRAINT ck_payment_outbox_counters
    CHECK (event_version > 0 AND publish_attempts >= 0);


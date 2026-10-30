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
    CONSTRAINT fk_payment_attempts_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id)
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
    CONSTRAINT fk_payment_refunds_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts (id)
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
    last_error VARCHAR(500)
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


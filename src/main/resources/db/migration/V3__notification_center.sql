-- 站内通知：业务事务内写出的待发布事件，以及消费者幂等写入的消息中心记录。
-- 只新增通知表，不修改 V1、V2 和已有业务表。

CREATE TABLE notification_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    appointment_order_id BIGINT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    last_error VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME,
    CONSTRAINT uk_notification_outbox_event UNIQUE (event_id),
    CONSTRAINT uk_notification_outbox_node UNIQUE (appointment_order_id, notification_type),
    CONSTRAINT fk_notification_outbox_appointment FOREIGN KEY (appointment_order_id) REFERENCES appointment_orders (id),
    CONSTRAINT ck_notification_outbox_type CHECK (notification_type IN (
        'APPOINTMENT_CONFIRMED', 'CHECKED_IN', 'VISIT_STARTED', 'VISIT_COMPLETED')),
    CONSTRAINT ck_notification_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_notification_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_notification_outbox_pending ON notification_outbox_events (status, next_attempt_at, id);

CREATE TABLE user_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    appointment_order_id BIGINT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(32) NOT NULL,
    patient_name VARCHAR(64) NOT NULL,
    occurred_at DATETIME NOT NULL,
    detail TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_notifications_event UNIQUE (event_id),
    CONSTRAINT uk_user_notifications_node UNIQUE (appointment_order_id, notification_type),
    CONSTRAINT fk_user_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_notifications_appointment FOREIGN KEY (appointment_order_id) REFERENCES appointment_orders (id),
    CONSTRAINT ck_user_notifications_type CHECK (notification_type IN (
        'APPOINTMENT_CONFIRMED', 'CHECKED_IN', 'VISIT_STARTED', 'VISIT_COMPLETED'))
);

CREATE INDEX idx_user_notifications_user_time ON user_notifications (user_id, occurred_at, id);

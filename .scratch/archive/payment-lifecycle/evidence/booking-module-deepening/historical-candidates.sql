-- 只读候选盘点；不能仅凭结果认定每条记录都属于迟到事件缺陷。
SELECT a.id AS appointment_order_id, p.id AS payment_order_id,
       a.status AS appointment_status, p.status AS payment_status,
       a.payment_expires_at, p.paid_at, p.updated_at,
       r.status AS refund_status, r.reason AS refund_reason,
       c.id AS active_claim_id, s.id AS schedule_id,
       s.total_capacity, s.available_capacity, s.held_capacity, s.confirmed_capacity
FROM appointment_orders a
JOIN payment_orders p ON p.appointment_order_id = a.id
LEFT JOIN payment_refunds r ON r.payment_order_id = p.id
LEFT JOIN appointment_active_claims c ON c.appointment_order_id = a.id
LEFT JOIN schedules s ON s.id = a.schedule_id
WHERE a.status = 'PENDING_PAYMENT'
  AND p.status IN ('REFUND_PENDING', 'REFUNDED')
ORDER BY a.id;

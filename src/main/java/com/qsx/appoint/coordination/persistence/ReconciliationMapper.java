package com.qsx.appoint.coordination.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 对账只读扫描；资金和预约修复仍经各模块 application interface。 */
@Mapper
public interface ReconciliationMapper {
    @Select("""
            SELECT a.id FROM appointment_orders a
            JOIN payment_orders p ON p.appointment_order_id = a.id
            WHERE a.status = 'PENDING_PAYMENT' AND p.status = 'PAID' AND p.updated_at <= #{cutoff}
            ORDER BY p.updated_at, a.id LIMIT #{limit}
            """)
    List<Long> selectPaidPendingAppointmentIds(@Param("cutoff") LocalDateTime cutoff,
                                                @Param("limit") int limit);

    @Select("""
            SELECT a.id FROM appointment_orders a
            JOIN payment_orders p ON p.appointment_order_id = a.id
            LEFT JOIN payment_refunds r ON r.payment_order_id = p.id
            WHERE a.status IN ('CANCELLED','EXPIRED','CANCELLED_BY_HOSPITAL')
              AND p.status = 'PAID' AND r.id IS NULL AND p.updated_at <= #{cutoff}
            ORDER BY p.updated_at, a.id LIMIT #{limit}
            """)
    List<Long> selectClosedPaidWithoutRefundAppointmentIds(@Param("cutoff") LocalDateTime cutoff,
                                                            @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM payment_outbox_events WHERE status IN ('PENDING','FAILED') AND occurred_at <= #{cutoff}")
    int countStaleOutbox(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT COUNT(*) FROM payment_refunds WHERE status IN ('PENDING','FAILED') AND updated_at <= #{cutoff}")
    int countStaleRefunds(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT COUNT(*) FROM payment_attempts WHERE status IN ('CREATED','PROCESSING') AND updated_at <= #{cutoff}")
    int countStaleAttempts(@Param("cutoff") LocalDateTime cutoff);

    @Select("""
            SELECT COUNT(*) FROM appointment_orders a
            JOIN payment_orders p ON p.appointment_order_id = a.id
            WHERE a.user_id <> p.user_id
               OR (a.fee_amount IS NOT NULL AND p.amount IS NOT NULL AND a.fee_amount <> p.amount)
               OR (a.fee_currency IS NOT NULL AND p.currency IS NOT NULL AND a.fee_currency <> p.currency)
            """)
    int countAssociationMismatches();
}

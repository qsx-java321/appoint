package com.qsx.appoint.coordination.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账只读候选与统计SQL，补偿写入仍通过稳定模块interface。
 * <p>候选可能在查询后变化，协调事务必须重新锁定判断；统计不提供修复授权。
 */
@Mapper
public interface ReconciliationMapper {
    /**
     * 读取支付更新时间不晚于阈值的PAID+PENDING_PAYMENT候选。
     * @param cutoff 滞留阈值，不是支付资格截止
     * @param limit 本批上限
     * @return 按支付更新时间、预约ID排序的候选预约ID
     */
    @Select("""
            SELECT a.id FROM appointment_orders a
            JOIN payment_orders p ON p.appointment_order_id = a.id
            WHERE a.status = 'PENDING_PAYMENT' AND p.status = 'PAID' AND p.updated_at <= #{cutoff}
            ORDER BY p.updated_at, a.id LIMIT #{limit}
            """)
    List<Long> selectPaidPendingAppointmentIds(@Param("cutoff") LocalDateTime cutoff,
                                                @Param("limit") int limit);

    /**
     * 读取已关闭、仍PAID且无退款单的滞留候选，包含历史停诊取消。
     * @param cutoff 支付更新时间上界
     * @param limit 本批上限
     * @return 候选预约ID，写入前仍需重新加锁判断
     */
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

    /**
     * 统计发生时间已超过阈值的PENDING/FAILED事件，不直接重置发布状态。
     * @return 该类滞留事件数
     */
    @Select("SELECT COUNT(*) FROM payment_outbox_events WHERE status IN ('PENDING','FAILED') AND occurred_at <= #{cutoff}")
    int countStaleOutbox(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 统计更新时间超过阈值的PENDING/FAILED退款，仅供人工项报告。
     * @return 该类退款数
     */
    @Select("SELECT COUNT(*) FROM payment_refunds WHERE status IN ('PENDING','FAILED') AND updated_at <= #{cutoff}")
    int countStaleRefunds(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 统计更新时间超过阈值的CREATED/PROCESSING尝试，不能据此断言渠道未收款。
     * @return 该类尝试数
     */
    @Select("SELECT COUNT(*) FROM payment_attempts WHERE status IN ('CREATED','PROCESSING') AND updated_at <= #{cutoff}")
    int countStaleAttempts(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 统计已关联预约/支付的账号、非空金额或币种不一致。
     * <p>SQL只统计INNER JOIN可关联记录，不声称覆盖所有缺失关联。
     * @return 不一致关联数，转人工检查，不直接改写资金事实
     */
    @Select("""
            SELECT COUNT(*) FROM appointment_orders a
            JOIN payment_orders p ON p.appointment_order_id = a.id
            WHERE a.user_id <> p.user_id
               OR (a.fee_amount IS NOT NULL AND p.amount IS NOT NULL AND a.fee_amount <> p.amount)
               OR (a.fee_currency IS NOT NULL AND p.currency IS NOT NULL AND a.fee_currency <> p.currency)
            """)
    int countAssociationMismatches();
}

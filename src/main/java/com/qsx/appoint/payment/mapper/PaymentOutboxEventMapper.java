package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentOutboxEventMapper extends BaseMapper<PaymentOutboxEvent> {
    @Select("""
            SELECT * FROM payment_outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= #{now}
            ORDER BY next_attempt_at, occurred_at, event_id
            LIMIT #{limit} FOR UPDATE SKIP LOCKED
            """)
    List<PaymentOutboxEvent> selectDueForUpdate(@Param("now") LocalDateTime now,
                                                 @Param("limit") int limit);

    @Update("""
            UPDATE payment_outbox_events
            SET status = 'PUBLISHED', published_at = #{publishedAt}, last_error = NULL
            WHERE event_id = #{eventId} AND status = 'PENDING'
            """)
    int markPublished(@Param("eventId") String eventId,
                      @Param("publishedAt") LocalDateTime publishedAt);

    @Update("""
            UPDATE payment_outbox_events
            SET status = #{status}, publish_attempts = #{attempts},
                next_attempt_at = #{nextAttemptAt}, last_error = #{lastError}
            WHERE event_id = #{eventId} AND status = 'PENDING'
            """)
    int recordFailure(@Param("eventId") String eventId,
                      @Param("status") String status,
                      @Param("attempts") int attempts,
                      @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                      @Param("lastError") String lastError);
}


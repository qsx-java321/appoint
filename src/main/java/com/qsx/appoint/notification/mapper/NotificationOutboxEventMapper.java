package com.qsx.appoint.notification.mapper;

import com.qsx.appoint.notification.entity.NotificationOutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待发布事件持久化入口；节点唯一键冲突时保留第一条，不抛给业务事务。
 */
@Mapper
public interface NotificationOutboxEventMapper {
    String COLUMNS = "id, event_id, appointment_order_id, notification_type, payload, status, attempts, "
            + "next_attempt_at, last_error, created_at, published_at";

    @Insert("""
            INSERT INTO notification_outbox_events
                (event_id, appointment_order_id, notification_type, payload, status, attempts,
                 next_attempt_at, created_at)
            VALUES
                (#{eventId}, #{appointmentOrderId}, #{notificationType}, #{payload}, #{status}, #{attempts},
                 #{nextAttemptAt}, #{createdAt})
            ON DUPLICATE KEY UPDATE event_id = event_id
            """)
    int insertIgnore(NotificationOutboxEvent event);

    @Select("SELECT " + COLUMNS + " FROM notification_outbox_events "
            + "WHERE status = 'PENDING' AND next_attempt_at <= #{now} ORDER BY id LIMIT #{limit}")
    List<NotificationOutboxEvent> selectPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE notification_outbox_events
            SET status = 'PUBLISHED', attempts = #{attempts}, last_error = NULL,
                published_at = #{publishedAt}, next_attempt_at = #{publishedAt}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markPublished(@Param("id") Long id, @Param("attempts") int attempts,
                      @Param("publishedAt") LocalDateTime publishedAt);

    @Update("""
            UPDATE notification_outbox_events
            SET attempts = #{attempts}, last_error = #{lastError}, next_attempt_at = #{nextAttemptAt}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markFailed(@Param("id") Long id, @Param("attempts") int attempts,
                   @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("lastError") String lastError);
}

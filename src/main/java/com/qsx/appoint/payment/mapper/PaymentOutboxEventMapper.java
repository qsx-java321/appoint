package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox批量锁领取和发布进度持久化，不负责实际Broker投递。
 * <p>仅PENDING可领取/更新，SKIP LOCKED使多个发布器跳过彼此锁住的事件。
 */
@Mapper
public interface PaymentOutboxEventMapper extends BaseMapper<PaymentOutboxEvent> {
    /**
     * 锁定到期PENDING事件，跳过其他发布器已锁事件。
     * @param now 本批领取时间
     * @param limit 本批数量上限
     * @return 按下次投递时间、发生时间、eventId排序的独占领取列表
     */
    @Select("""
            SELECT * FROM payment_outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= #{now}
            ORDER BY next_attempt_at, occurred_at, event_id
            LIMIT #{limit} FOR UPDATE SKIP LOCKED
            """)
    List<PaymentOutboxEvent> selectDueForUpdate(@Param("now") LocalDateTime now,
                                                 @Param("limit") int limit);

    /**
     * Broker确认成功后把PENDING标为PUBLISHED，清理最近错误。
     * @param eventId 原业务事件ID
     * @param publishedAt 本地标记时间，不代表消费完成
     * @return 影响行数，发布事务要求1
     */
    @Update("""
            UPDATE payment_outbox_events
            SET status = 'PUBLISHED', published_at = #{publishedAt}, last_error = NULL
            WHERE event_id = #{eventId} AND status = 'PENDING'
            """)
    int markPublished(@Param("eventId") String eventId,
                      @Param("publishedAt") LocalDateTime publishedAt);

    /**
     * 仅更新PENDING事件的失败进度，达到配置上限由调用方传FAILED。
     * @param attempts 本次累计发布失败次数
     * @param nextAttemptAt 退避后允许再次领取的时间
     * @param lastError 截断后的异常摘要
     * @return 影响行数，发布事务要求1
     */
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


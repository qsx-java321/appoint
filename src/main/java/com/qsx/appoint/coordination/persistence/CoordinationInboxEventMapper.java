package com.qsx.appoint.coordination.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 协调消费者Inbox登记，使用消费者名+eventId唯一键区分消费者处理凭证。
 * <p>插入与业务变更必须在同一事务；INSERT IGNORE不是可以脱离业务先提交的接收确认。
 */
@Mapper
public interface CoordinationInboxEventMapper extends BaseMapper<CoordinationInboxEvent> {
    /**
     * 按消费者+事件ID登记处理凭证，与业务写入一起提交或回滚。
     * @param consumerName 消费者稳定逻辑名
     * @param eventId 原业务事件UUID
     * @param eventType 稳定事件类型
     * @param processedAt 本次服务端处理时间
     * @return 首次登记1，唯一键重复0；只有0才走DUPLICATE短路
     */
    @Insert("""
            INSERT IGNORE INTO coordination_inbox_events
              (consumer_name, event_id, event_type, processed_at)
            VALUES (#{consumerName}, #{eventId}, #{eventType}, #{processedAt})
            """)
    int insertIgnore(@Param("consumerName") String consumerName,
                     @Param("eventId") String eventId,
                     @Param("eventType") String eventType,
                     @Param("processedAt") LocalDateTime processedAt);
}


package com.qsx.appoint.coordination.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface CoordinationInboxEventMapper extends BaseMapper<CoordinationInboxEvent> {
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


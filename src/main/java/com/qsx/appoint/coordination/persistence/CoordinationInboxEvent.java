package com.qsx.appoint.coordination.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 协调消费者的事件处理凭证。 */
@Getter @Setter @TableName("coordination_inbox_events")
public class CoordinationInboxEvent {
    private Long id;
    private String consumerName;
    private String eventId;
    private String eventType;
    private LocalDateTime processedAt;
}


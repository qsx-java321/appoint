package com.qsx.appoint.coordination.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 消费者处理凭证，与预约确认/关闭退款同事务提交，以消费者名+eventId去重。
 * <p>失败回滚凭证后可重试；不能删除凭证或改eventId绕过已完成业务。
 */
@Getter @Setter @TableName("coordination_inbox_events")
public class CoordinationInboxEvent {
    /** 本地记录主键。 */
    private Long id;
    /** 消费者逻辑名称，与eventId组成Inbox幂等键。 */
    private String consumerName;
    /** 事件UUID主键，发布重试和人工重放沿用同一值。 */
    private String eventId;
    /** 事件类型稳定名称，发布器按其选择固定v1路由。 */
    private String eventType;
    /** 本地处理记录时间。 */
    private LocalDateTime processedAt;
}


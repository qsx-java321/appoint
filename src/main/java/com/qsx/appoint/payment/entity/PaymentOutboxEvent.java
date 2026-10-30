package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 与支付状态同事务提交、等待可靠发布的领域事件。 */
@Getter @Setter @TableName("payment_outbox_events")
public class PaymentOutboxEvent {
    @TableId
    private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private Integer eventVersion;
    private String payload;
    private LocalDateTime occurredAt;
    private String status;
    private Integer publishAttempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime publishedAt;
    private String lastError;
}


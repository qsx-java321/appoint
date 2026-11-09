package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 与资金或退款事实同事务保存、等待至少一次可靠发布的事件。
 * <p>Broker确认与数据库标记存在故障窗口，重复事件由消费者Inbox/业务幂等保护。
 */
@Getter @Setter @TableName("payment_outbox_events")
public class PaymentOutboxEvent {
    /** 事件UUID主键，发布重试和人工重放沿用同一值。 */
    @TableId
    private String eventId;
    /** 事件聚合类型，支付事件当前为PaymentOrder。 */
    private String aggregateType;
    /** 所属支付聚合ID，不是预约ID或事件ID。 */
    private Long aggregateId;
    /** 事件类型稳定名称，发布器按其选择固定v1路由。 */
    private String eventType;
    /** 事件契约版本，目前为1。 */
    private Integer eventVersion;
    /** 已保存的完整事件JSON正文；发布时不重新生成业务事实。 */
    private String payload;
    /** 本系统形成该业务事件的时间。 */
    private LocalDateTime occurredAt;
    /** 发布状态PENDING/PUBLISHED/FAILED；FAILED等待运维恢复，不等于业务事务失败。 */
    private String status;
    /** Outbox累计发布失败次数，独立于消费者业务处理次数。 */
    private Integer publishAttempts;
    /** 下次允许领取发布的时间，退避计算后写入。 */
    private LocalDateTime nextAttemptAt;
    /** 本地标记Broker发布成功的时间，不代表消费者已完成。 */
    private LocalDateTime publishedAt;
    /** 最近发布异常摘要，最多500字符。 */
    private String lastError;
}


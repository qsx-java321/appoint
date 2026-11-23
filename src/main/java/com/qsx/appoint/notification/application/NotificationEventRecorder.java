package com.qsx.appoint.notification.application;

import com.qsx.appoint.notification.domain.NotificationType;

import java.time.LocalDateTime;

/**
 * 在业务事务内记录待发布通知事件。
 * <p>调用方必须已有事务；快照、文案或Outbox写入失败时异常向外传播，使业务整体回滚。
 */
public interface NotificationEventRecorder {
    /**
     * 记录一条通知节点事件；同一预约同一节点重复记录时保留第一条。
     * @param appointmentOrderId 预约单主键
     * @param type 通知节点
     * @param occurredAt 本次业务动作发生时间
     */
    void record(Long appointmentOrderId, NotificationType type, LocalDateTime occurredAt);
}

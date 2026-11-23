package com.qsx.appoint.notification.application;

/** 环境重置时清空通知主队列和死信队列，避免旧消息在数据库重建后继续投递。 */
public interface NotificationQueueResetter {
    void purge();
}

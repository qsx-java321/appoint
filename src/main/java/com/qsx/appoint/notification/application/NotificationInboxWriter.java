package com.qsx.appoint.notification.application;

/** 消费端幂等写入消息中心；重复事件或重复业务节点直接忽略。 */
public interface NotificationInboxWriter {
    void write(NotificationPayload payload);
}

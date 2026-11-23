package com.qsx.appoint.notification.messaging;

import com.qsx.appoint.notification.application.NotificationQueueResetter;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 通过RabbitAdmin清理通知队列；队列或管理端尚未就绪时直接跳过。 */
@Component
@RequiredArgsConstructor
class RabbitNotificationQueueResetter implements NotificationQueueResetter {
    private final ObjectProvider<AmqpAdmin> admins;

    @Override
    public void purge() {
        AmqpAdmin admin = admins.getIfAvailable();
        if (admin == null) return;
        purge(admin, NotificationMessagingConfig.QUEUE);
        purge(admin, NotificationMessagingConfig.DEAD_LETTER_QUEUE);
    }

    private void purge(AmqpAdmin admin, String queue) {
        if (admin.getQueueInfo(queue) != null) admin.purgeQueue(queue);
    }
}

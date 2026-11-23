package com.qsx.appoint.notification.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 环境重置只清理已存在的通知队列，Broker尚未就绪时不阻断重置。 */
class RabbitNotificationQueueResetterTest {
    private final AmqpAdmin admin = mock(AmqpAdmin.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AmqpAdmin> admins = mock(ObjectProvider.class);
    private final RabbitNotificationQueueResetter resetter = new RabbitNotificationQueueResetter(admins);

    @Test
    void purgesDeclaredMainAndDeadLetterQueues() {
        when(admins.getIfAvailable()).thenReturn(admin);
        when(admin.getQueueInfo(anyString())).thenReturn(mock(QueueInformation.class));

        resetter.purge();

        verify(admin).purgeQueue(NotificationMessagingConfig.QUEUE);
        verify(admin).purgeQueue(NotificationMessagingConfig.DEAD_LETTER_QUEUE);
    }

    @Test
    void skipsQueuesThatAreNotDeclared() {
        when(admins.getIfAvailable()).thenReturn(admin);
        when(admin.getQueueInfo(anyString())).thenReturn(null);

        resetter.purge();

        verify(admin, never()).purgeQueue(anyString());
    }

    @Test
    void skipsWhenBrokerAdminIsUnavailable() {
        when(admins.getIfAvailable()).thenReturn(null);

        resetter.purge();

        verify(admin, never()).getQueueInfo(anyString());
    }
}

package com.qsx.appoint.payment;

import com.qsx.appoint.coordination.persistence.CoordinationInboxEvent;
import com.qsx.appoint.coordination.persistence.CoordinationInboxEventMapper;
import com.qsx.appoint.payment.entity.PaymentNotification;
import com.qsx.appoint.payment.entity.PaymentOutboxEvent;
import com.qsx.appoint.payment.mapper.PaymentNotificationMapper;
import com.qsx.appoint.payment.mapper.PaymentOutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.expiration.enabled=false"
})
class PaymentPersistenceIntegrationTest {
    @Autowired private PaymentNotificationMapper notificationMapper;
    @Autowired private PaymentOutboxEventMapper outboxMapper;
    @Autowired private CoordinationInboxEventMapper inboxMapper;

    @Test
    @Transactional
    void persistencePrimitivesStoreAuditDeduplicateInboxAndLockDueOutbox() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 22, 0);
        PaymentNotification notification = new PaymentNotification();
        notification.setChannel("MOCK");
        notification.setNotificationId("notice-" + UUID.randomUUID());
        notification.setPayloadHash("hash");
        notification.setVerificationResult("VERIFIED");
        notification.setReceivedAt(now);
        notificationMapper.insert(notification);
        assertThat(notificationMapper.selectById(notification.getId()).getPayloadHash()).isEqualTo("hash");

        String eventId = UUID.randomUUID().toString();
        CoordinationInboxEvent inbox = new CoordinationInboxEvent();
        inbox.setConsumerName("appointment-confirmation");
        inbox.setEventId(eventId);
        inbox.setEventType("PaymentSucceeded");
        inbox.setProcessedAt(now);
        inboxMapper.insert(inbox);
        CoordinationInboxEvent duplicate = new CoordinationInboxEvent();
        duplicate.setConsumerName(inbox.getConsumerName());
        duplicate.setEventId(eventId);
        duplicate.setEventType(inbox.getEventType());
        duplicate.setProcessedAt(now);
        assertThatThrownBy(() -> inboxMapper.insert(duplicate)).isInstanceOf(DuplicateKeyException.class);

        PaymentOutboxEvent event = new PaymentOutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("PaymentOrder");
        event.setAggregateId(1L);
        event.setEventType("PaymentSucceeded");
        event.setEventVersion(1);
        event.setPayload("{}");
        event.setOccurredAt(now.minusSeconds(1));
        event.setStatus("PENDING");
        event.setPublishAttempts(0);
        event.setNextAttemptAt(now.minusSeconds(1));
        outboxMapper.insert(event);

        assertThat(outboxMapper.selectDueForUpdate(now, 10))
                .extracting(PaymentOutboxEvent::getEventId)
                .containsExactly(eventId);
    }
}


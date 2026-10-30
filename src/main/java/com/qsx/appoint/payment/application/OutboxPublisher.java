package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxPublishTransaction transaction;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${appoint.outbox.scan-delay:5s}")
    void scan() {
        if (properties.enabled()) transaction.publishDue();
    }

    public int publishDue() {
        return transaction.publishDue();
    }
}

package com.qsx.appoint.payment.application;

import com.qsx.appoint.payment.config.OutboxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox定时与手动发布入口，委托独立事务Bean进行批量领取和状态提交。
 * <p>enabled只控制定时入口；显式publishDue仍可触发一次发布，供运维/测试使用。
 * @see OutboxPublishTransaction
 */
@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxPublishTransaction transaction;
    private final OutboxProperties properties;
    /** 按配置周期触发发布；关闭enabled仅跳过定时触发，不影响显式发布用例。 */
    @Scheduled(fixedDelayString = "${appoint.outbox.scan-delay:5s}")
    void scan() {
        if (properties.enabled()) transaction.publishDue();
    }

    /**
     * 显式执行一批到期Outbox发布。
     * @return 本批确认并记录已发布的条数，不表示消费者已经完成业务
     */
    public int publishDue() {
        return transaction.publishDue();
    }
}

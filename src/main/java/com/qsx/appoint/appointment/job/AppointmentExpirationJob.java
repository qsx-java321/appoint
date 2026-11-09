package com.qsx.appoint.appointment.job;

import com.qsx.appoint.appointment.config.ExpirationProperties;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.mapper.ExpirationCandidate;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 待支付预约过期扫描入口；只找候选，每张预约交给协调层独立事务复验并处理。
 * <p>扫描时间在一轮内固定，使用截止时间+ID游标避免OFFSET在数据变化时漏扫。
 * 是否关闭仍以锁后支付事实为准，截止前已收款的候选可能被恢复确认为已预约。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentExpirationJob {
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final BookingLifecycleCoordinator lifecycleService;
    private final ExpirationProperties properties;
    private final Clock appointmentClock;

    /**
     * 按配置批量扫描本轮已经到期的待支付预约。
     * <p>单笔失败记录日志并继续；游标仍前进，失败记录留待下一轮重试，不令整轮回滚。
     * 日志closed统计协调层实际处理成功数，其中可能包含恢复确认，并非纯EXPIRED数量。
     */
    @Scheduled(fixedDelayString = "${appoint.expiration.scan-delay:30s}")
    public void scanExpiredAppointments() {
        if (!properties.enabled()) return;
        long started = System.nanoTime();
        LocalDateTime scanTime = LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime cursorExpiresAt = null;
        Long cursorId = null;
        int scanned = 0;
        int closed = 0;
        int failed = 0;

        while (true) {
            List<ExpirationCandidate> candidates = appointmentOrderMapper.selectExpirationCandidates(
                    scanTime, cursorExpiresAt, cursorId, properties.batchSize());
            if (candidates.isEmpty()) break;
            for (ExpirationCandidate candidate : candidates) {
                scanned++;
                try {
                    if (lifecycleService.expireIfDue(candidate.getId())) closed++;
                } catch (RuntimeException exception) {
                    failed++;
                    log.error("过期预约处理失败，appointmentOrderId={}", candidate.getId(), exception);
                }
                cursorExpiresAt = candidate.getPaymentExpiresAt();
                cursorId = candidate.getId();
            }
            if (candidates.size() < properties.batchSize()) break;
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        if (scanned > 0 || failed > 0) {
            log.info("预约过期扫描完成，scanned={}, closed={}, failed={}, elapsedMs={}",
                    scanned, closed, failed, elapsedMillis);
        }
    }
}

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

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentExpirationJob {
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final BookingLifecycleCoordinator lifecycleService;
    private final ExpirationProperties properties;
    private final Clock appointmentClock;

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

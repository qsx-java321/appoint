package com.qsx.appoint.coordination.application;

import com.qsx.appoint.coordination.persistence.ReconciliationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private final ReconciliationMapper mapper;
    private final BookingLifecycleCoordinator lifecycle;
    private final Clock appointmentClock;

    @Value("${appoint.reconciliation.stale-after:5m}")
    private Duration staleAfter;
    @Value("${appoint.reconciliation.batch-size:100}")
    private int batchSize;

    public ReconciliationReport runOnce() {
        LocalDateTime cutoff = LocalDateTime.now(appointmentClock)
                .minus(staleAfter).truncatedTo(ChronoUnit.SECONDS);
        List<Long> paidPending = mapper.selectPaidPendingAppointmentIds(cutoff, batchSize);
        int recovered = 0;
        int repairFailures = 0;
        for (Long appointmentId : paidPending) {
            try {
                if (lifecycle.reconcilePaidPending(appointmentId)) recovered++;
            } catch (RuntimeException exception) {
                repairFailures++;
                log.warn("payment_reconciliation_repair_failed type=paid_pending appointmentId={}",
                        appointmentId, exception);
            }
        }
        List<Long> missingRefunds = mapper.selectClosedPaidWithoutRefundAppointmentIds(cutoff, batchSize);
        int refundsCreated = 0;
        for (Long appointmentId : missingRefunds) {
            try {
                if (lifecycle.ensureRefundForClosedPaid(appointmentId)) refundsCreated++;
            } catch (RuntimeException exception) {
                repairFailures++;
                log.warn("payment_reconciliation_repair_failed type=missing_refund appointmentId={}",
                        appointmentId, exception);
            }
        }
        int staleOutbox = mapper.countStaleOutbox(cutoff);
        int staleRefunds = mapper.countStaleRefunds(cutoff);
        int staleAttempts = mapper.countStaleAttempts(cutoff);
        int mismatches = mapper.countAssociationMismatches();
        // 这些记录不能在没有渠道查询结果或人工判断时安全改写资金事实。
        int manualItems = staleOutbox + staleRefunds + staleAttempts + mismatches + repairFailures;
        return new ReconciliationReport(paidPending.size(), recovered, missingRefunds.size(), refundsCreated,
                staleOutbox, staleRefunds, staleAttempts, mismatches, repairFailures, manualItems);
    }
}

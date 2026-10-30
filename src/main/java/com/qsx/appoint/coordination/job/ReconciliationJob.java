package com.qsx.appoint.coordination.job;

import com.qsx.appoint.coordination.application.ReconciliationReport;
import com.qsx.appoint.coordination.application.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJob {
    private final ReconciliationService service;

    @Scheduled(fixedDelayString = "${appoint.reconciliation.scan-delay:60s}")
    public void scan() {
        ReconciliationReport report = service.runOnce();
        log.info("支付对账完成，paidPendingFound={}, paidPendingRecovered={}, "
                        + "closedPaidWithoutRefundFound={}, refundsCreated={}, staleOutbox={}, "
                        + "staleRefunds={}, staleAttempts={}, associationMismatches={}, "
                        + "repairFailures={}, manualItems={}",
                report.paidPendingFound(), report.paidPendingRecovered(),
                report.closedPaidWithoutRefundFound(), report.refundsCreated(), report.staleOutbox(),
                report.staleRefunds(), report.staleAttempts(), report.associationMismatches(),
                report.repairFailures(), report.manualItems());
    }
}

package com.qsx.appoint.coordination.job;

import com.qsx.appoint.coordination.application.ReconciliationReport;
import com.qsx.appoint.coordination.application.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付对账的定时Adapter，按配置启用并输出结构化扫描结果。
 * <p>具体补偿由Service和生命周期事务完成；定时入口不直接写订单或修正库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "appoint.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationJob {
    private final ReconciliationService service;
    /** 执行一轮对账并记录发现/修复/人工项；分类计数可能重叠，不等同去重订单总数。 */
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

package com.qsx.appoint.coordination.application;

/** 一次对账扫描的结构化结果，可直接用于日志、指标桥接和运维检查。 */
public record ReconciliationReport(
        int paidPendingFound,
        int paidPendingRecovered,
        int closedPaidWithoutRefundFound,
        int refundsCreated,
        int staleOutbox,
        int staleRefunds,
        int staleAttempts,
        int associationMismatches,
        int repairFailures,
        int manualItems) {
}

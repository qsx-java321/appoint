package com.qsx.appoint.coordination.application;

/**
 * 一轮对账发现与处理统计；各类别可能重叠，不代表去重订单数。
 * @param paidPendingFound 已支付待确认候选数
 * @param paidPendingRecovered 实际处理的已付待确认数，包含确认和迟到关闭退款
 * @param closedPaidWithoutRefundFound 已关闭已付且无退款单候选数
 * @param refundsCreated 协调接口处理为true的漏退款候选数
 * @param staleOutbox 超过阈值的PENDING/FAILED Outbox数
 * @param staleRefunds 超过阈值的PENDING/FAILED退款数
 * @param staleAttempts 超过阈值的CREATED/PROCESSING尝试数
 * @param associationMismatches 账号、非空金额或币种关联不符数
 * @param repairFailures 本轮候选修复异常次数
 * @param manualItems 滞留、关联异常和修复失败的分类合计，不是去重记录数
 */
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

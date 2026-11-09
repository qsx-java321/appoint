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

/**
 * 支付最终一致性的补偿扫描：恢复已付待确认，补齐已关闭已付的漏退款，并报告人工项。
 * <p>只读扫描候选不代表仍满足条件；每单交给生命周期事务重新加锁判断。
 * 长期Outbox/退款/尝试及关联异常仅计数告警，不猜测渠道结果或直接修改资金/库存。
 */
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

    /**
     * 执行一轮有批量上限的对账，单笔修复失败不阻断其他候选。
     * <p>截止以当前时间减staleAfter计算；普通扫描不包住整批写事务。
     * @return 本轮发现、实际处理及人工项统计；分类可能重叠，manualItems不是去重订单数
     */
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

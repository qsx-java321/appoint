package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.domain.AppointmentCloseReason;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentActiveClaimMapper;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.IdentityHashMap;

/**
 * 预约生命周期的数据库实现：在外层事务中维护库存、预约状态、有效占用和就诊序号。
 *
 * <p>协调层只调用完整动作；本类内部固定场次→预约锁序。锁定Case以对象身份绑定当前事务，
 * 不能把只读快照或上一次事务的Case拿来写。条件更新任一步失败都会抛异常，使外层整笔回滚。
 * @see AppointmentLifecycle
 */
@Service
@RequiredArgsConstructor
class DefaultAppointmentLifecycle implements AppointmentLifecycle {
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final AppointmentActiveClaimMapper activeClaimMapper;
    private final ScheduleMapper scheduleMapper;

    /**
     * {@inheritDoc}
     * <p>只读取预约，不建立事务写授权；存储状态在转换时显式映射为领域枚举。
     */
    @Override
    public AppointmentLifecycleCase find(Long id) { return view(appointmentOrderMapper.selectByIdForLookup(id)); }

    /**
     * {@inheritDoc}
     * <p>先只读定位场次，再按固定锁序锁场次和预约，并在锁后复验关联。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AppointmentLifecycleCase lock(Long id) {
        AppointmentOrder lookup = appointmentOrderMapper.selectByIdForLookup(id);
        if (lookup == null) return null;
        Schedule schedule = scheduleMapper.selectByIdForUpdate(lookup.getScheduleId());
        if (schedule == null || schedule.getSessionType() == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        AppointmentOrder order = appointmentOrderMapper.selectByIdForUpdate(id);
        if (order == null) return null;
        if (!order.getScheduleId().equals(schedule.getId())) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        AppointmentLifecycleCase appointment = view(order);
        context(true).locked.put(appointment, schedule);
        return appointment;
    }

    /**
     * {@inheritDoc}
     * <p>使用上下文保存的已锁场次分配序号；库存和预约条件更新都必须恰好影响一行。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AppointmentConfirmation confirm(AppointmentLifecycleCase appointment) {
        Schedule schedule = requireLocked(appointment, AppointmentStatus.PENDING_PAYMENT);
        // 场次行锁串行化序号分配；确认和库存写入同属外层事务，失败不能只消耗序号。
        int visitNumber = schedule.getNextVisitNumber();
        requireOne(scheduleMapper.confirmOne(schedule.getId(), visitNumber));
        requireOne(appointmentOrderMapper.confirmIfPending(appointment.id(), visitNumber));
        // 一次完整写入后撤销此Case的写授权，后续操作必须重新lock获取最新事实。
        context(false).locked.remove(appointment);
        return new AppointmentConfirmation(appointment.id(), visitNumber);
    }

    /**
     * {@inheritDoc}
     * <p>释放暂占、推进预约终态、删除占用构成一个操作；占用缺失也视为完整性错误。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closePending(AppointmentLifecycleCase appointment, AppointmentCloseReason reason, LocalDateTime closedAt) {
        Schedule schedule = requireLocked(appointment, AppointmentStatus.PENDING_PAYMENT);
        requireOne(scheduleMapper.releaseHeldOne(schedule.getId()));
        requireOne(appointmentOrderMapper.closeIfPending(appointment.id(), reason.status().name(), closedAt));
        requireOne(activeClaimMapper.deleteByAppointmentOrderId(appointment.id()));
        context(false).locked.remove(appointment);
    }

    /**
     * 校验Case的对象身份和预期状态，返回当前事务锁住的场次。
     * <p>不按record值相等匹配，防止复制或伪造Case绕过锁定前置条件。
     * @return 与该Case绑定的已锁场次；不满足契约时抛INTERNAL_ERROR
     */
    private Schedule requireLocked(AppointmentLifecycleCase appointment, AppointmentStatus expected) {
        Context context = context(false);
        Schedule schedule = context == null ? null : context.locked.get(appointment);
        if (schedule == null || appointment.status() != expected) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        return schedule;
    }

    /**
     * 查找或首次登记本事务的预约写上下文。
     * <p>使用事务同步器，随事务挂起/恢复；REQUIRES_NEW不能借用外层事务的锁定结果。
     * @param create true表示不存在时登记；false表示只查询
     * @return 当前事务上下文；未登记且create=false时为null
     */
    private Context context(boolean create) {
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof Context context) return context;
        }
        if (!create) return null;
        Context context = new Context();
        TransactionSynchronizationManager.registerSynchronization(context);
        return context;
    }
    private static final class Context implements TransactionSynchronization {
        final IdentityHashMap<AppointmentLifecycleCase, Schedule> locked = new IdentityHashMap<>();
        @Override public void afterCompletion(int status) { locked.clear(); }
    }

    /**
     * 将内部Entity转换为稳定业务事实，不把库存或持久化对象暴露给协调层。
     * @return 对应Case；输入预约不存在时返回null
     */
    private AppointmentLifecycleCase view(AppointmentOrder order) {
        if (order == null) return null;
        return new AppointmentLifecycleCase(order.getId(), order.getUserId(), order.getScheduleId(),
                AppointmentStatus.fromStored(order.getStatus()), order.getPaymentExpiresAt(), order.getAppointmentDate(),
                order.getStartTime(), order.getVisitNumber(), order.getDoctorId(), order.getEndTime());
    }
    /** 条件写入必须恰好命中一行；否则抛完整性错误，由外层事务统一回滚。 */
    private void requireOne(int count) { if (count != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
}

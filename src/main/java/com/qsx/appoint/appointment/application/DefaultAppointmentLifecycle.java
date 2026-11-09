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

@Service
@RequiredArgsConstructor
class DefaultAppointmentLifecycle implements AppointmentLifecycle {
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final AppointmentActiveClaimMapper activeClaimMapper;
    private final ScheduleMapper scheduleMapper;

    @Override
    public AppointmentLifecycleCase find(Long id) { return view(appointmentOrderMapper.selectByIdForLookup(id)); }

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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AppointmentConfirmation confirm(AppointmentLifecycleCase appointment) {
        Schedule schedule = requireLocked(appointment, AppointmentStatus.PENDING_PAYMENT);
        int visitNumber = schedule.getNextVisitNumber();
        requireOne(scheduleMapper.confirmOne(schedule.getId(), visitNumber));
        requireOne(appointmentOrderMapper.confirmIfPending(appointment.id(), visitNumber));
        context(false).locked.remove(appointment);
        return new AppointmentConfirmation(appointment.id(), visitNumber);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closePending(AppointmentLifecycleCase appointment, AppointmentCloseReason reason, LocalDateTime closedAt) {
        Schedule schedule = requireLocked(appointment, AppointmentStatus.PENDING_PAYMENT);
        requireOne(scheduleMapper.releaseHeldOne(schedule.getId()));
        requireOne(appointmentOrderMapper.closeIfPending(appointment.id(), reason.status().name(), closedAt));
        requireOne(activeClaimMapper.deleteByAppointmentOrderId(appointment.id()));
        context(false).locked.remove(appointment);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelConfirmed(AppointmentLifecycleCase appointment, LocalDateTime cancelledAt) {
        Schedule schedule = requireLocked(appointment, AppointmentStatus.CONFIRMED);
        requireOne(scheduleMapper.releaseConfirmedOne(schedule.getId()));
        requireOne(appointmentOrderMapper.cancelIfConfirmed(appointment.id(), cancelledAt));
        requireOne(activeClaimMapper.deleteByAppointmentOrderId(appointment.id()));
        context(false).locked.remove(appointment);
    }

    private Schedule requireLocked(AppointmentLifecycleCase appointment, AppointmentStatus expected) {
        Context context = context(false);
        Schedule schedule = context == null ? null : context.locked.get(appointment);
        if (schedule == null || appointment.status() != expected) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        return schedule;
    }

    /** 同步器随事务挂起/恢复；身份Map拒绝find快照、伪造对象及跨事务缓存。 */
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
    private AppointmentLifecycleCase view(AppointmentOrder order) {
        if (order == null) return null;
        return new AppointmentLifecycleCase(order.getId(), order.getUserId(), order.getScheduleId(),
                AppointmentStatus.fromStored(order.getStatus()), order.getPaymentExpiresAt(), order.getAppointmentDate(),
                order.getStartTime(), order.getVisitNumber());
    }
    private void requireOne(int count) { if (count != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
}

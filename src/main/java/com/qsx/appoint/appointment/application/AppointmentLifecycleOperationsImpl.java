package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentActiveClaimMapper;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class AppointmentLifecycleOperationsImpl implements AppointmentLifecycleOperations {
    private final AppointmentOrderMapper appointmentOrderMapper;
    private final AppointmentActiveClaimMapper activeClaimMapper;
    private final ScheduleMapper scheduleMapper;

    @Override
    public AppointmentLifecycleView findAppointment(Long id) {
        return view(appointmentOrderMapper.selectByIdForLookup(id));
    }

    @Override
    public ScheduleLifecycleView lockSchedule(Long id) {
        Schedule schedule = scheduleMapper.selectByIdForUpdate(id);
        if (schedule == null || schedule.getSessionType() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new ScheduleLifecycleView(schedule.getId(), schedule.getStatus(), schedule.getNextVisitNumber());
    }

    @Override
    public AppointmentLifecycleView lockAppointment(Long id) {
        return view(appointmentOrderMapper.selectByIdForUpdate(id));
    }

    @Override
    public List<AppointmentLifecycleView> lockPendingAppointments(Long scheduleId) {
        return appointmentOrderMapper.selectPendingByScheduleForUpdate(scheduleId).stream().map(this::view).toList();
    }

    @Override
    public void confirmInventory(Long scheduleId, Integer visitNumber) {
        requireOne(scheduleMapper.confirmOne(scheduleId, visitNumber));
    }

    @Override
    public void confirmAppointment(Long id, Integer visitNumber) {
        requireOne(appointmentOrderMapper.confirmIfPending(id, visitNumber));
    }

    @Override
    public void releaseHeldInventory(Long scheduleId) {
        requireOne(scheduleMapper.releaseHeldOne(scheduleId));
    }

    @Override
    public void closePendingAppointment(Long id, String status, LocalDateTime closedAt) {
        requireOne(appointmentOrderMapper.closeIfPending(id, status, closedAt));
        requireOne(activeClaimMapper.deleteByAppointmentOrderId(id));
    }

    @Override
    public void releaseConfirmedInventory(Long scheduleId) {
        requireOne(scheduleMapper.releaseConfirmedOne(scheduleId));
    }

    @Override
    public void cancelConfirmedAppointment(Long id, LocalDateTime closedAt) {
        requireOne(appointmentOrderMapper.cancelIfConfirmed(id, closedAt));
        requireOne(activeClaimMapper.deleteByAppointmentOrderId(id));
    }

    @Override
    public void closeSchedule(Long scheduleId, Long operatorUserId, LocalDateTime closedAt) {
        requireOne(scheduleMapper.closeIfOpen(scheduleId, closedAt, operatorUserId));
    }

    private AppointmentLifecycleView view(AppointmentOrder order) {
        if (order == null) return null;
        return new AppointmentLifecycleView(order.getId(), order.getUserId(), order.getScheduleId(),
                order.getStatus(), order.getPaymentExpiresAt(), order.getAppointmentDate(),
                order.getStartTime(), order.getVisitNumber());
    }

    private void requireOne(int updated) {
        if (updated != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}

package com.qsx.appoint.inventory.application;

import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.catalog.model.AppointmentSession;
import com.qsx.appoint.catalog.model.BookingTimeRules;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 基于 schedules 表的库存端口实现：复用现有条件更新 SQL，保持原锁序和事务边界。
 *
 * <p>所有写方法加入调用方事务；占号时间只来自 {@link ReserveCommand#checkedAt()}，
 * 不在库存内部重新采样，避免等待就诊人锁期间跨截止时改变既有语义。
 */
@Service
@RequiredArgsConstructor
class DefaultScheduleInventory implements ScheduleInventory {
    private final ScheduleMapper scheduleMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InventorySnapshot reserve(ReserveCommand command) {
        AppointmentSession session = AppointmentSession.require(command.sessionType());
        Long scheduleId = findOrCreateScheduleId(command, session);
        var dates = BookingTimeRules.selectableDates(command.checkedAt());
        LocalDateTime earliestStart = command.checkedAt().plus(BookingTimeRules.MINIMUM_BOOKING_ADVANCE);
        if (scheduleMapper.holdOneBookableSession(scheduleId, command.doctorId(), command.appointmentDate(),
                session.name(), dates.getFirst(), dates.getLast(),
                earliestStart.toLocalDate(), earliestStart.toLocalTime()) != 1) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
        // 占号后重新读取当前行，不能用占号前的对象推算库存或费用。
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        if (schedule.getRegistrationFee() == null || schedule.getRegistrationFee().signum() <= 0
                || !"CNY".equals(schedule.getFeeCurrency())) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
        return snapshot(schedule);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InventorySnapshot lock(Long scheduleId) {
        return snapshot(scheduleMapper.selectByIdForUpdate(scheduleId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int confirm(Long scheduleId, int expectedNextVisitNumber) {
        if (scheduleMapper.confirmOne(scheduleId, expectedNextVisitNumber) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return expectedNextVisitNumber;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long scheduleId) {
        if (scheduleMapper.releaseHeldOne(scheduleId) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 仅首次预约从统一设置生成场次；建场后加锁读取，包含其他事务刚提交的场次。
     */
    private Long findOrCreateScheduleId(ReserveCommand command, AppointmentSession session) {
        Schedule schedule = scheduleMapper.selectByDoctorDateSession(
                command.doctorId(), command.appointmentDate(), session.name());
        if (schedule == null) {
            scheduleMapper.insertGeneratedFromSettings(command.doctorId(), command.appointmentDate(),
                    session.startTime(), session.endTime(), session.name());
            schedule = scheduleMapper.selectByDoctorDateSessionForUpdate(
                    command.doctorId(), command.appointmentDate(), session.name());
            if (schedule == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return schedule.getId();
    }

    private InventorySnapshot snapshot(Schedule schedule) {
        if (schedule == null) return null;
        return new InventorySnapshot(schedule.getId(), schedule.getAppointmentDate(), schedule.getStartTime(),
                schedule.getEndTime(), schedule.getRegistrationFee(), schedule.getFeeCurrency(),
                schedule.getNextVisitNumber());
    }
}

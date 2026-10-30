package com.qsx.appoint.catalog.service.impl;

import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.catalog.dto.*;
import com.qsx.appoint.catalog.entity.Doctor;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.entity.ScheduleInventoryAdjustment;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.catalog.mapper.ScheduleInventoryAdjustmentMapper;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.catalog.service.ScheduleManagementService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ScheduleManagementServiceImpl implements ScheduleManagementService {
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleInventoryAdjustmentMapper adjustmentMapper;
    private final BookingLifecycleCoordinator lifecycleService;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public AdminScheduleResponse create(Long operatorUserId, CreateScheduleRequest request) {
        requireDoctor(request.getDoctorId());
        SessionTimes times = sessionTimes(request.getSessionType());
        validateFutureWindow(request.getAppointmentDate(), times.start());
        BigDecimal registrationFee = requireRegistrationFee(request.getRegistrationFee(), request.getFeeCurrency());
        Schedule schedule = new Schedule();
        schedule.setDoctorId(request.getDoctorId());
        schedule.setAppointmentDate(request.getAppointmentDate());
        schedule.setSessionType(times.type());
        schedule.setStartTime(times.start());
        schedule.setEndTime(times.end());
        schedule.setStatus("OPEN");
        schedule.setTotalCapacity(request.getTotalCapacity());
        schedule.setAvailableCapacity(request.getTotalCapacity());
        schedule.setHeldCapacity(0);
        schedule.setConfirmedCapacity(0);
        schedule.setNextVisitNumber(1);
        schedule.setRegistrationFee(registrationFee);
        schedule.setFeeCurrency("CNY");
        try {
            scheduleMapper.insert(schedule);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
        return response(schedule);
    }

    @Override
    @Transactional
    public AdminScheduleResponse update(Long scheduleId, UpdateScheduleRequest request) {
        Schedule schedule = requireNewScheduleForUpdate(scheduleId);
        LocalDateTime now = now();
        if (!now.isBefore(releaseAt(schedule.getAppointmentDate()))) {
            throw new BusinessException(ErrorCode.SCHEDULE_IDENTITY_LOCKED);
        }
        if (request.getDoctorId() == null && request.getAppointmentDate() == null
                && request.getSessionType() == null && request.getRegistrationFee() == null
                && request.getFeeCurrency() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Long doctorId = request.getDoctorId() == null ? schedule.getDoctorId() : request.getDoctorId();
        LocalDate date = request.getAppointmentDate() == null
                ? schedule.getAppointmentDate() : request.getAppointmentDate();
        SessionTimes times = request.getSessionType() == null
                ? sessionTimes(schedule.getSessionType()) : sessionTimes(request.getSessionType());
        requireDoctor(doctorId);
        validateFutureWindow(date, times.start());
        if (!now.isBefore(releaseAt(date))) {
            throw new BusinessException(ErrorCode.SCHEDULE_IDENTITY_LOCKED);
        }
        schedule.setDoctorId(doctorId);
        schedule.setAppointmentDate(date);
        schedule.setSessionType(times.type());
        schedule.setStartTime(times.start());
        schedule.setEndTime(times.end());
        if (request.getRegistrationFee() != null || request.getFeeCurrency() != null) {
            BigDecimal fee = request.getRegistrationFee() == null
                    ? schedule.getRegistrationFee() : request.getRegistrationFee();
            String currency = request.getFeeCurrency() == null
                    ? schedule.getFeeCurrency() : request.getFeeCurrency();
            schedule.setRegistrationFee(requireRegistrationFee(fee, currency));
            schedule.setFeeCurrency("CNY");
        }
        try {
            scheduleMapper.updateById(schedule);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.SCHEDULE_UNAVAILABLE);
        }
        return response(scheduleMapper.selectById(scheduleId));
    }

    @Override
    @Transactional
    public AdminScheduleResponse adjustInventory(Long operatorUserId, Long scheduleId,
                                                 AdjustScheduleInventoryRequest request) {
        if (request.getDelta() == 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        Schedule schedule = requireNewScheduleForUpdate(scheduleId);
        if (!"OPEN".equals(schedule.getStatus())) throw new BusinessException(ErrorCode.SCHEDULE_CLOSED);
        if (!now().isBefore(startAt(schedule))) throw new BusinessException(ErrorCode.SCHEDULE_STARTED);
        if (request.getDelta() < 0 && schedule.getAvailableCapacity() < -request.getDelta()) {
            throw new BusinessException(ErrorCode.INVENTORY_REDUCTION_EXCEEDS_AVAILABLE);
        }
        if (scheduleMapper.adjustAvailable(scheduleId, request.getDelta()) != 1) {
            throw new BusinessException(ErrorCode.INVENTORY_REDUCTION_EXCEEDS_AVAILABLE);
        }
        Schedule updated = scheduleMapper.selectById(scheduleId);
        ScheduleInventoryAdjustment audit = new ScheduleInventoryAdjustment();
        audit.setScheduleId(scheduleId);
        audit.setOperatorUserId(operatorUserId);
        audit.setDelta(request.getDelta());
        audit.setReason(request.getReason().trim());
        audit.setTotalCapacityAfter(updated.getTotalCapacity());
        audit.setAvailableCapacityAfter(updated.getAvailableCapacity());
        audit.setCreatedAt(now());
        adjustmentMapper.insert(audit);
        return response(updated);
    }

    @Override
    @Transactional
    public AdminScheduleResponse close(Long operatorUserId, Long scheduleId) {
        Schedule schedule = requireNewScheduleForUpdate(scheduleId);
        if (!"OPEN".equals(schedule.getStatus())) throw new BusinessException(ErrorCode.SCHEDULE_CLOSED);
        LocalDateTime now = now();
        if (!now.isBefore(startAt(schedule))) throw new BusinessException(ErrorCode.SCHEDULE_STARTED);
        lifecycleService.closeScheduleLocked(schedule.getId(), operatorUserId, now);
        return response(scheduleMapper.selectById(scheduleId));
    }

    private Schedule requireNewScheduleForUpdate(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectByIdForUpdate(scheduleId);
        if (schedule == null || schedule.getSessionType() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return schedule;
    }

    private void requireDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private void validateFutureWindow(LocalDate date, LocalTime start) {
        LocalDateTime now = now();
        LocalDateTime startAt = LocalDateTime.of(date, start);
        if (!now.isBefore(startAt)) throw new BusinessException(ErrorCode.SCHEDULE_STARTED);
        if (!now.isBefore(startAt.minusMinutes(30))) {
            throw new BusinessException(ErrorCode.SCHEDULE_CUTOFF_REACHED);
        }
    }

    private LocalDateTime releaseAt(LocalDate date) {
        return LocalDateTime.of(date.minusDays(7), LocalTime.of(8, 0));
    }

    private LocalDateTime startAt(Schedule schedule) {
        return LocalDateTime.of(schedule.getAppointmentDate(), schedule.getStartTime());
    }

    private SessionTimes sessionTimes(String raw) {
        if (raw == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        String type = raw.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "MORNING" -> new SessionTimes(type, LocalTime.of(8, 0), LocalTime.of(12, 0));
            case "AFTERNOON" -> new SessionTimes(type, LocalTime.of(14, 0), LocalTime.of(17, 0));
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }

    private BigDecimal requireRegistrationFee(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || currency == null || !"CNY".equals(currency.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return amount.setScale(2);
    }

    private AdminScheduleResponse response(Schedule schedule) {
        return new AdminScheduleResponse(schedule.getId(), schedule.getDoctorId(), schedule.getAppointmentDate(),
                schedule.getSessionType(), schedule.getStartTime(), schedule.getEndTime(), schedule.getStatus(),
                schedule.getTotalCapacity(), schedule.getAvailableCapacity(), schedule.getHeldCapacity(),
                schedule.getConfirmedCapacity(), schedule.getNextVisitNumber(), schedule.getRegistrationFee(),
                schedule.getFeeCurrency(), schedule.getClosedAt(),
                schedule.getClosedBy());
    }

    private record SessionTimes(String type, LocalTime start, LocalTime end) { }
}

package com.qsx.appoint.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.catalog.dto.*;
import com.qsx.appoint.catalog.entity.*;
import com.qsx.appoint.catalog.mapper.*;
import com.qsx.appoint.catalog.model.AppointmentSession;
import com.qsx.appoint.catalog.model.BookingTimeRules;
import com.qsx.appoint.catalog.model.ScheduleAvailability;
import com.qsx.appoint.catalog.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 演示数据查询实现，刻意保持一层清晰的数据映射。 */
@Service @RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {
    private final HospitalMapper hospitalMapper;
    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;
    private final BookingSettingsMapper bookingSettingsMapper;
    private final Clock appointmentClock;

    public List<HospitalResponse> listHospitals() { return hospitalMapper.selectList(null).stream().map(x -> new HospitalResponse(x.getId(), x.getName(), x.getAddress())).toList(); }
    public List<DepartmentResponse> listDepartments(Long hospitalId) { return departmentMapper.selectList(new LambdaQueryWrapper<Department>().eq(Department::getHospitalId, hospitalId)).stream().map(x -> new DepartmentResponse(x.getId(), x.getName())).toList(); }
    public List<DoctorResponse> listDoctors(Long departmentId) { return doctorMapper.selectList(new LambdaQueryWrapper<Doctor>().eq(Doctor::getDepartmentId, departmentId)).stream().map(x -> new DoctorResponse(x.getId(), x.getDoctorNo(), x.getName(), x.getTitle(), x.getIntroduction())).toList(); }
    @Transactional(readOnly = true)
    public List<ScheduleResponse> listSchedules(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) return List.of();
        LocalDateTime serverTime = LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
        List<LocalDate> dates = BookingTimeRules.selectableDates(serverTime);
        List<String> sessionTypes = java.util.Arrays.stream(AppointmentSession.values())
                .map(Enum::name).toList();
        Map<SessionKey, Schedule> persisted = new HashMap<>();
        scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getDoctorId, doctorId)
                        .between(Schedule::getAppointmentDate, dates.getFirst(), dates.getLast())
                        .in(Schedule::getSessionType, sessionTypes))
                .forEach(schedule -> persisted.put(
                        new SessionKey(schedule.getAppointmentDate(), schedule.getSessionType()), schedule));
        BookingSettings settings = bookingSettingsMapper.selectById((short) 1);
        if (settings == null) throw new com.qsx.appoint.common.BusinessException(
                com.qsx.appoint.common.ErrorCode.INTERNAL_ERROR);

        java.util.ArrayList<ScheduleResponse> result = new java.util.ArrayList<>(dates.size() * sessionTypes.size());
        for (LocalDate date : dates) {
            for (AppointmentSession session : AppointmentSession.values()) {
                Schedule schedule = persisted.get(new SessionKey(date, session.name()));
                int total = schedule == null ? settings.getCapacityPerSession() : schedule.getTotalCapacity();
                int available = schedule == null ? settings.getCapacityPerSession() : schedule.getAvailableCapacity();
                java.math.BigDecimal fee = schedule == null
                        ? settings.getRegistrationFee() : schedule.getRegistrationFee();
                String currency = schedule == null ? settings.getCurrency() : schedule.getFeeCurrency();
                ScheduleAvailability availability = availability(date, session.startTime(), available, serverTime);
                result.add(new ScheduleResponse(schedule == null ? null : schedule.getId(),
                        doctorId, doctor.getDoctorNo(), date, session.name(),
                        session.startTime(), session.endTime(), total, available, fee, currency,
                        availability == ScheduleAvailability.AVAILABLE,
                        BookingTimeRules.bookingClosesAt(date, session.startTime()), availability, serverTime));
            }
        }
        return result;
    }

    private ScheduleAvailability availability(LocalDate date, LocalTime startTime, int available,
                                               LocalDateTime serverTime) {
        if (!serverTime.isBefore(date.atTime(startTime))) return ScheduleAvailability.STARTED;
        if (!BookingTimeRules.canCreate(date, startTime, serverTime)) return ScheduleAvailability.CUTOFF_REACHED;
        return available > 0 ? ScheduleAvailability.AVAILABLE : ScheduleAvailability.FULL;
    }

    private record SessionKey(LocalDate date, String sessionType) { }
}

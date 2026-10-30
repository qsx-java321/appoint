package com.qsx.appoint.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.catalog.dto.*;
import com.qsx.appoint.catalog.entity.*;
import com.qsx.appoint.catalog.mapper.*;
import com.qsx.appoint.catalog.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 演示数据查询实现，刻意保持一层清晰的数据映射。 */
@Service @RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {
    private final HospitalMapper hospitalMapper;
    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;
    private final Clock appointmentClock;

    public List<HospitalResponse> listHospitals() { return hospitalMapper.selectList(null).stream().map(x -> new HospitalResponse(x.getId(), x.getName(), x.getAddress())).toList(); }
    public List<DepartmentResponse> listDepartments(Long hospitalId) { return departmentMapper.selectList(new LambdaQueryWrapper<Department>().eq(Department::getHospitalId, hospitalId)).stream().map(x -> new DepartmentResponse(x.getId(), x.getName())).toList(); }
    public List<DoctorResponse> listDoctors(Long departmentId) { return doctorMapper.selectList(new LambdaQueryWrapper<Doctor>().eq(Doctor::getDepartmentId, departmentId)).stream().map(x -> new DoctorResponse(x.getId(), x.getDoctorNo(), x.getName(), x.getTitle(), x.getIntroduction())).toList(); }
    public List<ScheduleResponse> listSchedules(Long doctorId) {
        LocalDateTime now = LocalDateTime.now(appointmentClock);
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) return List.of();
        return scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getDoctorId, doctorId)
                        .isNotNull(Schedule::getSessionType)
                        .and(query -> query.gt(Schedule::getAppointmentDate, now.toLocalDate())
                                .or(sameDay -> sameDay.eq(Schedule::getAppointmentDate, now.toLocalDate())
                                        .gt(Schedule::getEndTime, now.toLocalTime())))
                        .orderByAsc(Schedule::getAppointmentDate, Schedule::getStartTime))
                .stream().map(schedule -> response(schedule, doctor.getDoctorNo(), now)).toList();
    }

    private ScheduleResponse response(Schedule schedule, String doctorNo, LocalDateTime now) {
        LocalDateTime releaseAt = LocalDateTime.of(schedule.getAppointmentDate().minusDays(7), LocalTime.of(8, 0));
        LocalDateTime cutoffAt = LocalDateTime.of(schedule.getAppointmentDate(), schedule.getStartTime()).minusMinutes(30);
        String availability;
        if ("CLOSED".equals(schedule.getStatus())) availability = "CLOSED";
        else if (now.isBefore(releaseAt)) availability = "NOT_OPEN";
        else if (!now.isBefore(cutoffAt)) availability = "CUTOFF_REACHED";
        else if (schedule.getAvailableCapacity() == null || schedule.getAvailableCapacity() <= 0) availability = "FULL";
        else availability = "AVAILABLE";
        return new ScheduleResponse(schedule.getId(), schedule.getDoctorId(), doctorNo,
                schedule.getAppointmentDate(), schedule.getSessionType(), schedule.getStartTime(), schedule.getEndTime(),
                schedule.getStatus(), schedule.getTotalCapacity(), schedule.getAvailableCapacity(),
                schedule.getRegistrationFee(), schedule.getFeeCurrency(), availability,
                "AVAILABLE".equals(availability), releaseAt, cutoffAt);
    }
}

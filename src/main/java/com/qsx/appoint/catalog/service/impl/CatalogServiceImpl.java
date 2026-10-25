package com.qsx.appoint.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.catalog.dto.*;
import com.qsx.appoint.catalog.entity.*;
import com.qsx.appoint.catalog.mapper.*;
import com.qsx.appoint.catalog.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** 演示数据查询实现，刻意保持一层清晰的数据映射。 */
@Service @RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {
    private final HospitalMapper hospitalMapper;
    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;

    public List<HospitalResponse> listHospitals() { return hospitalMapper.selectList(null).stream().map(x -> new HospitalResponse(x.getId(), x.getName(), x.getAddress())).toList(); }
    public List<DepartmentResponse> listDepartments(Long hospitalId) { return departmentMapper.selectList(new LambdaQueryWrapper<Department>().eq(Department::getHospitalId, hospitalId)).stream().map(x -> new DepartmentResponse(x.getId(), x.getName())).toList(); }
    public List<DoctorResponse> listDoctors(Long departmentId) { return doctorMapper.selectList(new LambdaQueryWrapper<Doctor>().eq(Doctor::getDepartmentId, departmentId)).stream().map(x -> new DoctorResponse(x.getId(), x.getDoctorNo(), x.getName(), x.getTitle(), x.getIntroduction())).toList(); }
    public List<ScheduleResponse> listAvailableSchedules(Long doctorId) { return scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>().eq(Schedule::getDoctorId, doctorId).eq(Schedule::getStatus, "AVAILABLE")).stream().map(x -> new ScheduleResponse(x.getId(), x.getAppointmentDate(), x.getStartTime(), x.getEndTime(), x.getStatus())).toList(); }
}

package com.qsx.appoint.catalog.service;

import com.qsx.appoint.catalog.dto.*;
import java.util.List;

/** 面向预约流程提供只读的医院、医生与号源查询。 */
public interface CatalogService {
    List<HospitalResponse> listHospitals();
    List<DepartmentResponse> listDepartments(Long hospitalId);
    List<DoctorResponse> listDoctors(Long departmentId);
    List<ScheduleResponse> listAvailableSchedules(Long doctorId);
}

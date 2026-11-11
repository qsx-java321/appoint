package com.qsx.appoint.catalog.service;

import com.qsx.appoint.catalog.dto.*;
import java.util.List;

/** 面向预约流程提供只读的医院、医生与号源查询。 */
public interface CatalogService {
    List<HospitalResponse> listHospitals();
    List<DepartmentResponse> listDepartments(Long hospitalId);
    List<DoctorResponse> listDoctors(Long departmentId);
    /** 上海时间今天至后天的十二项场次快照，含截止与可预约状态；查询不生成场次。 */
    List<ScheduleResponse> listSchedules(Long doctorId);
}

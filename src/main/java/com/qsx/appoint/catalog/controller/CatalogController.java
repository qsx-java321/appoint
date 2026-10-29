package com.qsx.appoint.catalog.controller;

import com.qsx.appoint.catalog.dto.*;
import com.qsx.appoint.catalog.service.CatalogService;
import com.qsx.appoint.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 登录用户查询医院资源的入口。 */
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogService;
    @GetMapping("/hospitals") public Result<List<HospitalResponse>> hospitals() { return Result.success(catalogService.listHospitals()); }
    @GetMapping("/hospitals/{hospitalId}/departments") public Result<List<DepartmentResponse>> departments(@PathVariable Long hospitalId) { return Result.success(catalogService.listDepartments(hospitalId)); }
    @GetMapping("/departments/{departmentId}/doctors") public Result<List<DoctorResponse>> doctors(@PathVariable Long departmentId) { return Result.success(catalogService.listDoctors(departmentId)); }
    @GetMapping("/doctors/{doctorId}/schedules") public Result<List<ScheduleResponse>> schedules(@PathVariable Long doctorId) { return Result.success(catalogService.listSchedules(doctorId)); }
}

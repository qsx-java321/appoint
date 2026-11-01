package com.qsx.appoint.catalog.controller;

import com.qsx.appoint.catalog.dto.AdminDepartmentResponse;
import com.qsx.appoint.catalog.dto.AdminDoctorResponse;
import com.qsx.appoint.catalog.dto.AdminHospitalResponse;
import com.qsx.appoint.catalog.dto.CreateDepartmentRequest;
import com.qsx.appoint.catalog.dto.CreateDoctorRequest;
import com.qsx.appoint.catalog.dto.CreateHospitalRequest;
import com.qsx.appoint.catalog.dto.UpdateDepartmentRequest;
import com.qsx.appoint.catalog.dto.UpdateDoctorProfileRequest;
import com.qsx.appoint.catalog.dto.UpdateHospitalRequest;
import com.qsx.appoint.catalog.service.CatalogManagementService;
import com.qsx.appoint.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CatalogManagementController {
    private final CatalogManagementService service;

    @PostMapping("/hospitals")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AdminHospitalResponse> createHospital(@Valid @RequestBody CreateHospitalRequest request) {
        return Result.success(service.createHospital(request));
    }

    @PatchMapping("/hospitals/{hospitalId}")
    public Result<AdminHospitalResponse> updateHospital(@PathVariable Long hospitalId,
                                                         @Valid @RequestBody UpdateHospitalRequest request) {
        return Result.success(service.updateHospital(hospitalId, request));
    }

    @DeleteMapping("/hospitals/{hospitalId}")
    public Result<Void> deleteHospital(@PathVariable Long hospitalId) {
        service.deleteHospital(hospitalId);
        return Result.success(null);
    }

    @PostMapping("/hospitals/{hospitalId}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AdminDepartmentResponse> createDepartment(@PathVariable Long hospitalId,
                                                             @Valid @RequestBody CreateDepartmentRequest request) {
        return Result.success(service.createDepartment(hospitalId, request));
    }

    @PatchMapping("/departments/{departmentId}")
    public Result<AdminDepartmentResponse> updateDepartment(@PathVariable Long departmentId,
                                                             @Valid @RequestBody UpdateDepartmentRequest request) {
        return Result.success(service.updateDepartment(departmentId, request));
    }

    @DeleteMapping("/departments/{departmentId}")
    public Result<Void> deleteDepartment(@PathVariable Long departmentId) {
        service.deleteDepartment(departmentId);
        return Result.success(null);
    }

    @PostMapping("/departments/{departmentId}/doctors")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AdminDoctorResponse> createDoctor(@PathVariable Long departmentId,
                                                     @Valid @RequestBody CreateDoctorRequest request) {
        return Result.success(service.createDoctor(departmentId, request));
    }

    @PatchMapping("/doctors/{doctorId}")
    public Result<AdminDoctorResponse> updateDoctor(@PathVariable Long doctorId,
                                                     @Valid @RequestBody UpdateDoctorProfileRequest request) {
        return Result.success(service.updateDoctor(doctorId, request));
    }

    @DeleteMapping("/doctors/{doctorId}")
    public Result<Void> deleteDoctor(@PathVariable Long doctorId) {
        service.deleteDoctor(doctorId);
        return Result.success(null);
    }
}

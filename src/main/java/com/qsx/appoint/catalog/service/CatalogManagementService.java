package com.qsx.appoint.catalog.service;

import com.qsx.appoint.catalog.dto.AdminDepartmentResponse;
import com.qsx.appoint.catalog.dto.AdminDoctorResponse;
import com.qsx.appoint.catalog.dto.AdminHospitalResponse;
import com.qsx.appoint.catalog.dto.CreateDepartmentRequest;
import com.qsx.appoint.catalog.dto.CreateDoctorRequest;
import com.qsx.appoint.catalog.dto.CreateHospitalRequest;
import com.qsx.appoint.catalog.dto.UpdateDepartmentRequest;
import com.qsx.appoint.catalog.dto.UpdateDoctorProfileRequest;
import com.qsx.appoint.catalog.dto.UpdateHospitalRequest;

public interface CatalogManagementService {
    AdminHospitalResponse createHospital(CreateHospitalRequest request);
    AdminHospitalResponse updateHospital(Long hospitalId, UpdateHospitalRequest request);
    void deleteHospital(Long hospitalId);
    AdminDepartmentResponse createDepartment(Long hospitalId, CreateDepartmentRequest request);
    AdminDepartmentResponse updateDepartment(Long departmentId, UpdateDepartmentRequest request);
    void deleteDepartment(Long departmentId);
    AdminDoctorResponse createDoctor(Long departmentId, CreateDoctorRequest request);
    AdminDoctorResponse updateDoctor(Long doctorId, UpdateDoctorProfileRequest request);
    void deleteDoctor(Long doctorId);
}

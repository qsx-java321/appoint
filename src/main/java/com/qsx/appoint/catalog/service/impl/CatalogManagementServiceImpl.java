package com.qsx.appoint.catalog.service.impl;

import com.qsx.appoint.catalog.dto.AdminDepartmentResponse;
import com.qsx.appoint.catalog.dto.AdminDoctorResponse;
import com.qsx.appoint.catalog.dto.AdminHospitalResponse;
import com.qsx.appoint.catalog.dto.CreateDepartmentRequest;
import com.qsx.appoint.catalog.dto.CreateDoctorRequest;
import com.qsx.appoint.catalog.dto.CreateHospitalRequest;
import com.qsx.appoint.catalog.dto.UpdateDepartmentRequest;
import com.qsx.appoint.catalog.dto.UpdateDoctorProfileRequest;
import com.qsx.appoint.catalog.dto.UpdateHospitalRequest;
import com.qsx.appoint.catalog.entity.Department;
import com.qsx.appoint.catalog.entity.Doctor;
import com.qsx.appoint.catalog.entity.Hospital;
import com.qsx.appoint.catalog.mapper.DepartmentMapper;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.catalog.mapper.HospitalMapper;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.catalog.service.CatalogManagementService;
import com.qsx.appoint.catalog.service.DoctorNumberGenerator;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogManagementServiceImpl implements CatalogManagementService {
    private final HospitalMapper hospitalMapper;
    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorNumberGenerator doctorNumberGenerator;

    @Override
    @Transactional
    public AdminHospitalResponse createHospital(CreateHospitalRequest request) {
        Hospital hospital = new Hospital();
        hospital.setName(required(request.getName()));
        hospital.setAddress(required(request.getAddress()));
        hospitalMapper.insert(hospital);
        return new AdminHospitalResponse(hospital.getId(), hospital.getName(), hospital.getAddress());
    }

    @Override
    @Transactional
    public AdminHospitalResponse updateHospital(Long hospitalId, UpdateHospitalRequest request) {
        Hospital hospital = hospitalMapper.selectByIdForUpdate(hospitalId);
        if (hospital == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (request.getName() == null && request.getAddress() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (request.getName() != null) hospital.setName(required(request.getName()));
        if (request.getAddress() != null) hospital.setAddress(required(request.getAddress()));
        hospitalMapper.updateById(hospital);
        return hospitalResponse(hospital);
    }

    @Override
    @Transactional
    public void deleteHospital(Long hospitalId) {
        if (hospitalMapper.selectByIdForUpdate(hospitalId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (departmentMapper.countByHospitalId(hospitalId) > 0) {
            throw new BusinessException(ErrorCode.CATALOG_RESOURCE_IN_USE);
        }
        deleteOrResourceInUse(() -> hospitalMapper.deleteById(hospitalId));
    }

    @Override
    @Transactional
    public AdminDepartmentResponse createDepartment(Long hospitalId, CreateDepartmentRequest request) {
        if (hospitalMapper.selectByIdForUpdate(hospitalId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Department department = new Department();
        department.setHospitalId(hospitalId);
        department.setName(required(request.getName()));
        try {
            departmentMapper.insert(department);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NAME_EXISTS);
        }
        return new AdminDepartmentResponse(department.getId(), department.getHospitalId(), department.getName());
    }

    @Override
    @Transactional
    public AdminDepartmentResponse updateDepartment(Long departmentId, UpdateDepartmentRequest request) {
        Department department = departmentMapper.selectByIdForUpdate(departmentId);
        if (department == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (request.getName() == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        department.setName(required(request.getName()));
        try {
            departmentMapper.updateById(department);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NAME_EXISTS);
        }
        return departmentResponse(department);
    }

    @Override
    @Transactional
    public void deleteDepartment(Long departmentId) {
        if (departmentMapper.selectByIdForUpdate(departmentId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (doctorMapper.countByDepartmentId(departmentId) > 0) {
            throw new BusinessException(ErrorCode.CATALOG_RESOURCE_IN_USE);
        }
        deleteOrResourceInUse(() -> departmentMapper.deleteById(departmentId));
    }

    @Override
    @Transactional
    public AdminDoctorResponse createDoctor(Long departmentId, CreateDoctorRequest request) {
        if (departmentMapper.selectByIdForUpdate(departmentId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Doctor doctor = new Doctor();
        doctor.setDoctorNo(doctorNumberGenerator.nextDoctorNo());
        doctor.setDepartmentId(departmentId);
        doctor.setName(required(request.getName()));
        doctor.setTitle(required(request.getTitle()));
        doctor.setIntroduction(optionalProvided(request.getIntroduction()));
        doctorMapper.insert(doctor);
        return doctorResponse(doctor);
    }

    @Override
    @Transactional
    public AdminDoctorResponse updateDoctor(Long doctorId, UpdateDoctorProfileRequest request) {
        Doctor doctor = doctorMapper.selectByIdForUpdate(doctorId);
        if (doctor == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (request.getName() == null && request.getTitle() == null && request.getIntroduction() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (request.getName() != null) doctor.setName(required(request.getName()));
        if (request.getTitle() != null) doctor.setTitle(required(request.getTitle()));
        if (request.getIntroduction() != null) doctor.setIntroduction(optionalProvided(request.getIntroduction()));
        doctorMapper.updateById(doctor);
        return doctorResponse(doctor);
    }

    @Override
    @Transactional
    public void deleteDoctor(Long doctorId) {
        if (doctorMapper.selectByIdForUpdate(doctorId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (scheduleMapper.countByDoctorId(doctorId) > 0) {
            throw new BusinessException(ErrorCode.CATALOG_RESOURCE_IN_USE);
        }
        deleteOrResourceInUse(() -> doctorMapper.deleteById(doctorId));
    }

    private void deleteOrResourceInUse(Runnable deletion) {
        try {
            deletion.run();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CATALOG_RESOURCE_IN_USE);
        }
    }

    private AdminHospitalResponse hospitalResponse(Hospital hospital) {
        return new AdminHospitalResponse(hospital.getId(), hospital.getName(), hospital.getAddress());
    }

    private AdminDepartmentResponse departmentResponse(Department department) {
        return new AdminDepartmentResponse(department.getId(), department.getHospitalId(), department.getName());
    }

    private AdminDoctorResponse doctorResponse(Doctor doctor) {
        return new AdminDoctorResponse(doctor.getId(), doctor.getDoctorNo(), doctor.getDepartmentId(),
                doctor.getName(), doctor.getTitle(), doctor.getIntroduction());
    }

    private String required(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return value.trim();
    }

    private String optionalProvided(String value) {
        if (value == null) return null;
        return required(value);
    }
}

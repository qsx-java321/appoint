package com.qsx.appoint.catalog.service.impl;

import com.qsx.appoint.catalog.dto.DoctorAccountResponse;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.catalog.service.DoctorAccountService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.user.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class DoctorAccountServiceImpl implements DoctorAccountService {
    private final DoctorMapper doctors;
    private final UserRoleService roles;

    @Override
    @Transactional
    public DoctorAccountResponse bindAccount(Long doctorId, Long userId) {
        var doctor = doctors.selectByIdForUpdate(doctorId);
        if (doctor == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (doctor.getUserId() != null) throw new BusinessException(ErrorCode.DOCTOR_ACCOUNT_ALREADY_BOUND);
        roles.grantDoctorRole(userId);
        try {
            if (doctors.bindAccountIfUnbound(doctorId, userId) != 1) {
                throw new BusinessException(ErrorCode.DOCTOR_ACCOUNT_ALREADY_BOUND);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DOCTOR_ACCOUNT_ALREADY_BOUND);
        }
        return new DoctorAccountResponse(doctorId, userId);
    }

    @Override
    public Long requireDoctorId(Long userId) {
        var doctor = doctors.selectByUserId(userId);
        if (doctor == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        return doctor.getId();
    }
}

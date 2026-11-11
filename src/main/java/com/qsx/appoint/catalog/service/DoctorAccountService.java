package com.qsx.appoint.catalog.service;

import com.qsx.appoint.catalog.dto.DoctorAccountResponse;

/** 医生账号首次绑定及登录身份解析，不向调用者暴露医生实体。 */
public interface DoctorAccountService {
    DoctorAccountResponse bindAccount(Long doctorId, Long userId);
    Long requireDoctorId(Long userId);
}

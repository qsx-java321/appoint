package com.qsx.appoint.catalog.dto;

import java.util.List;

/** 当前环境的医生与登录账号对应关系，不包含密码或密码哈希。 */
public record DoctorAccountListResponse(String environment, List<Item> items) {
    public record Item(Long hospitalId, String hospitalName, Long departmentId, String departmentName,
                       Long doctorId, String doctorNo, String doctorName, Long userId, String email,
                       String bindingStatus, String passwordHint) {}
}

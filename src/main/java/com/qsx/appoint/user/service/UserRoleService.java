package com.qsx.appoint.user.service;

/** 内部角色授予契约；由受控的医生账号绑定用例调用。 */
public interface UserRoleService {
    void grantDoctorRole(Long userId);
}

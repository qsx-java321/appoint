package com.qsx.appoint.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.user.entity.Role;
import com.qsx.appoint.user.entity.UserRole;
import com.qsx.appoint.user.mapper.RoleMapper;
import com.qsx.appoint.user.mapper.UserMapper;
import com.qsx.appoint.user.mapper.UserRoleMapper;
import com.qsx.appoint.user.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class UserRoleServiceImpl implements UserRoleService {
    private final UserMapper users;
    private final RoleMapper roles;
    private final UserRoleMapper userRoles;

    @Override
    @Transactional
    public void grantDoctorRole(Long userId) {
        if (users.selectByIdForUpdate(userId) == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        Role role = roles.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "DOCTOR"));
        if (role == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        if (userRoles.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId).eq(UserRole::getRoleId, role.getId())) == 0) {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            userRoles.insert(relation);
        }
    }
}

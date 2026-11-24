package com.qsx.appoint.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.user.dto.UserResponse;
import com.qsx.appoint.user.entity.Role;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.entity.UserRole;
import com.qsx.appoint.user.mapper.RoleMapper;
import com.qsx.appoint.user.mapper.UserMapper;
import com.qsx.appoint.user.mapper.UserRoleMapper;
import com.qsx.appoint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 集中处理用户与角色的读取逻辑。
 * 角色关联由此处加载，避免认证和订单模块各自拼接查询规则。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    @Override
    public UserResponse getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return new UserResponse(user.getId(), user.getEmail(), getRoleCodes(userId), avatarUrl(user));
    }

    private String avatarUrl(User user) {
        if (user.getAvatarKey() == null) {
            return null;
        }
        String fileName = user.getAvatarKey().substring(user.getAvatarKey().lastIndexOf('/') + 1);
        return "/api/v1/users/" + user.getId() + "/avatar?v=" + fileName;
    }

    @Override
    public User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
            new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, userId)
        ).stream()
            .map(UserRole::getRoleId)
            .toList();

        if (roleIds.isEmpty()) {
        return List.of();
    }

    return roleMapper.selectByIds(roleIds)
            .stream()
            .map(Role::getCode)
            .toList();
}
}

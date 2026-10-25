package com.qsx.appoint.user.service;

import com.qsx.appoint.user.dto.UserResponse;
import com.qsx.appoint.user.entity.User;

import java.util.List;

/** 用户查询的业务边界，认证模块也通过此接口读取当前用户资料。 */
public interface UserService {
    UserResponse getCurrentUser(Long userId);
    User findByEmail(String email);
    List<String> getRoleCodes(Long userId);
}

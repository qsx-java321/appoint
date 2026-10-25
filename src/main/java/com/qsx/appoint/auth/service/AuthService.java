package com.qsx.appoint.auth.service;

import com.qsx.appoint.auth.dto.LoginRequest;
import com.qsx.appoint.auth.dto.LoginResponse;
import com.qsx.appoint.auth.dto.RegisterRequest;
import com.qsx.appoint.user.dto.UserResponse;

/** 注册和登录流程的服务接口。 */
public interface AuthService {
    UserResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}

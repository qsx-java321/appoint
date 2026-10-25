package com.qsx.appoint.auth.dto;

import com.qsx.appoint.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录成功后返回的 Token 元数据及用户基本资料。 */
@Getter
@AllArgsConstructor
public class LoginResponse {
    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;
    private final UserResponse user;
}

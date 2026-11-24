package com.qsx.appoint.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 对外返回的用户信息，不包含任何密码字段。 */
@Getter
@AllArgsConstructor
public class UserResponse {
    private final Long id;
    private final String email;
    private final List<String> roles;
    private final String avatarUrl;
}

package com.qsx.appoint.user.controller;

import com.qsx.appoint.common.Result;
import com.qsx.appoint.user.dto.UserResponse;
import com.qsx.appoint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户的只读接口。 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public Result<UserResponse> me(Authentication authentication) {
        return Result.success(userService.getCurrentUser((Long) authentication.getPrincipal()));
    }
}

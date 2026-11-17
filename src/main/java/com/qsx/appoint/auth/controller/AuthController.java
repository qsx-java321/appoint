package com.qsx.appoint.auth.controller;

import com.qsx.appoint.auth.dto.LoginRequest;
import com.qsx.appoint.auth.dto.LoginResponse;
import com.qsx.appoint.auth.dto.RegisterRequest;
import com.qsx.appoint.auth.dto.RegistrationCodeResponse;
import com.qsx.appoint.auth.dto.SendRegistrationCodeRequest;
import com.qsx.appoint.auth.dto.SignupTicketResponse;
import com.qsx.appoint.auth.dto.VerifyRegistrationCodeRequest;
import com.qsx.appoint.auth.service.AuthService;
import com.qsx.appoint.auth.service.RegistrationVerificationService;
import com.qsx.appoint.common.Result;
import com.qsx.appoint.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 对外提供注册和登录的匿名访问入口。 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final RegistrationVerificationService verification;

    @PostMapping("/register/code")
    public Result<RegistrationCodeResponse> sendRegistrationCode(
            @Valid @RequestBody SendRegistrationCodeRequest request) {
        return Result.success(verification.sendCode(request.email()));
    }

    @PostMapping("/register/verify")
    public Result<SignupTicketResponse> verifyRegistrationCode(
            @Valid @RequestBody VerifyRegistrationCodeRequest request) {
        return Result.success(verification.verifyCode(request.email(), request.code()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}

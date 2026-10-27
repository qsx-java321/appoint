package com.qsx.appoint.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.auth.dto.LoginRequest;
import com.qsx.appoint.auth.dto.LoginResponse;
import com.qsx.appoint.auth.dto.RegisterRequest;
import com.qsx.appoint.auth.service.AuthService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.security.JwtProperties;
import com.qsx.appoint.security.JwtTokenProvider;
import com.qsx.appoint.security.UserPrincipal;
import com.qsx.appoint.user.dto.UserResponse;
import com.qsx.appoint.user.entity.Role;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.entity.UserRole;
import com.qsx.appoint.user.mapper.RoleMapper;
import com.qsx.appoint.user.mapper.UserMapper;
import com.qsx.appoint.user.mapper.UserRoleMapper;
import com.qsx.appoint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** 注册和登录的应用服务，密码与 Token 只在此处处理。 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userService.findByEmail(email) != null) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
        Role userRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "USER"));
        if (userRole == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(userRole.getId());
        userRoleMapper.insert(relation);
        return userService.getCurrentUser(user.getId());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            String email = normalizeEmail(request.getEmail());
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.getPassword()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            UserResponse user = userService.getCurrentUser(principal.getUser().getId());
            String token = jwtTokenProvider.createToken(user.getId(), user.getEmail(), user.getRoles());
            return new LoginResponse(token, "Bearer", jwtProperties.getExpirationSeconds(), user);
        } catch (BadCredentialsException exception) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

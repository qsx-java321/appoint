package com.qsx.appoint.security;

import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 从本地用户、角色表中加载账号密码认证所需的身份信息。 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new UserPrincipal(user, userService.getRoleCodes(user.getId()).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }
}

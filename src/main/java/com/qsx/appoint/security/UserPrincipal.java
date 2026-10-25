package com.qsx.appoint.security;

import com.qsx.appoint.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/** Spring Security 在账号密码认证阶段使用的用户身份对象。 */
@Getter
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails {
    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getEmail(); }
}

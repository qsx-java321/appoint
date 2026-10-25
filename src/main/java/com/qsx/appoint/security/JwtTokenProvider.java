package com.qsx.appoint.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * 统一负责 JWT 签发与校验。
 * Token 保存必要身份信息，因此每次请求不需要重新查询用户和角色表。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;

    public String createToken(Long userId, String email, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationSeconds() * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSecretKey())
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(getSecretKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}

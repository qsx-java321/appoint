package com.qsx.appoint.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT 的本地配置，密钥通过环境变量提供，不能写入日志。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "appoint.jwt")
public class JwtProperties {
    private String secret;
    private long expirationSeconds;
}

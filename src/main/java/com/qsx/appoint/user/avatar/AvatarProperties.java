package com.qsx.appoint.user.avatar;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 本地 S3 对象存储连接参数和头像校验上限。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "appoint.avatar")
public class AvatarProperties {
    private String endpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private long maxBytes = 2 * 1024 * 1024;
    private int maxDimension = 4096;
}

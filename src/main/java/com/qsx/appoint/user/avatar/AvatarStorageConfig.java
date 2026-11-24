package com.qsx.appoint.user.avatar;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/** 为 SeaweedFS 创建 path-style S3 客户端，业务层只注入 AvatarStorage。 */
@Configuration
public class AvatarStorageConfig {

    @Bean
    public S3Client avatarS3Client(AvatarProperties properties) {
        if (properties.getEndpoint() == null || properties.getRegion() == null
                || properties.getAccessKey() == null || properties.getSecretKey() == null
                || properties.getBucket() == null) {
            throw new IllegalStateException("缺少头像对象存储配置 appoint.avatar.*");
        }
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                // SeaweedFS 4.39 不接受 SDK 默认附加的校验和与 aws-chunked 分块编码。
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    public AvatarStorage avatarStorage(S3Client avatarS3Client, AvatarProperties properties) {
        return new SeaweedS3AvatarStorage(avatarS3Client, properties);
    }
}

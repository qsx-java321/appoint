package com.qsx.appoint.user.avatar;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/** 通过 S3 兼容接口访问 SeaweedFS，bucket 由本地 Compose 启动时预创建。 */
@Slf4j
@RequiredArgsConstructor
public class SeaweedS3AvatarStorage implements AvatarStorage {
    private final S3Client s3Client;
    private final AvatarProperties properties;

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            log.error("上传头像对象失败，key={}", key, exception);
            throw new BusinessException(ErrorCode.AVATAR_STORAGE_ERROR);
        }
    }

    @Override
    public Optional<AvatarObject> get(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            return Optional.of(new AvatarObject(response.asByteArray(), response.response().contentType()));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            log.error("读取头像对象失败，key={}", key, exception);
            throw new BusinessException(ErrorCode.AVATAR_STORAGE_ERROR);
        } catch (SdkException exception) {
            log.error("读取头像对象失败，key={}", key, exception);
            throw new BusinessException(ErrorCode.AVATAR_STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (SdkException exception) {
            // 旧对象删除失败只留下孤立文件，不阻断用户已成功的头像操作。
            log.warn("删除旧头像对象失败，key={}", key, exception);
        }
    }
}

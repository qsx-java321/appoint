package com.qsx.appoint.user.service.impl;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.user.avatar.AvatarImageValidator;
import com.qsx.appoint.user.avatar.AvatarObject;
import com.qsx.appoint.user.avatar.AvatarResource;
import com.qsx.appoint.user.avatar.AvatarStorage;
import com.qsx.appoint.user.avatar.ValidatedAvatar;
import com.qsx.appoint.user.dto.UserResponse;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import com.qsx.appoint.user.service.UserAvatarService;
import com.qsx.appoint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 头像对象先写入存储，再更新数据库；数据库失败时删除新对象补偿。
 * 旧对象只在数据库更新成功后尽力删除，删除失败不反向影响用户操作。
 */
@Service
@RequiredArgsConstructor
public class UserAvatarServiceImpl implements UserAvatarService {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final UserMapper userMapper;
    private final AvatarStorage avatarStorage;
    private final AvatarImageValidator avatarImageValidator;
    private final UserService userService;
    private final Clock appointmentClock;

    @Override
    public UserResponse updateAvatar(Long userId, MultipartFile file) {
        User current = requireUser(userId);
        ValidatedAvatar avatar = avatarImageValidator.validate(file);
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + avatar.extension();
        avatarStorage.put(key, avatar.content(), avatar.contentType());
        try {
            LocalDateTime now = now();
            int updated = userMapper.updateAvatar(userId, key, avatar.contentType(), now);
            if (updated != 1) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        } catch (RuntimeException exception) {
            avatarStorage.delete(key);
            throw exception;
        }
        avatarStorage.delete(current.getAvatarKey());
        return userService.getCurrentUser(userId);
    }

    @Override
    public UserResponse removeAvatar(Long userId) {
        User current = requireUser(userId);
        if (current.getAvatarKey() == null) {
            return userService.getCurrentUser(userId);
        }
        userMapper.clearAvatar(userId, now());
        avatarStorage.delete(current.getAvatarKey());
        return userService.getCurrentUser(userId);
    }

    @Override
    public AvatarResource getAvatar(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getAvatarKey() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        AvatarObject object = avatarStorage.get(user.getAvatarKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String contentType = object.contentType() == null ? user.getAvatarContentType() : object.contentType();
        if (contentType == null) {
            contentType = DEFAULT_CONTENT_TYPE;
        }
        String fileName = user.getAvatarKey().substring(user.getAvatarKey().lastIndexOf('/') + 1);
        return new AvatarResource(object.content(), contentType, "\"" + fileName + "\"");
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}

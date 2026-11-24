package com.qsx.appoint.user.service;

import com.qsx.appoint.user.avatar.AvatarResource;
import com.qsx.appoint.user.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

/** 当前账号头像的替换、删除和公开读取边界。 */
public interface UserAvatarService {
    UserResponse updateAvatar(Long userId, MultipartFile file);

    UserResponse removeAvatar(Long userId);

    AvatarResource getAvatar(Long userId);
}

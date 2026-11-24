package com.qsx.appoint.user.controller;

import com.qsx.appoint.common.Result;
import com.qsx.appoint.user.avatar.AvatarResource;
import com.qsx.appoint.user.dto.UserResponse;
import com.qsx.appoint.user.service.UserAvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/** 当前账号头像的替换、删除，以及无需登录的图片读取。 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAvatarController {
    private static final CacheControl AVATAR_CACHE = CacheControl.maxAge(Duration.ofDays(365))
            .cachePublic()
            .immutable();

    private final UserAvatarService avatarService;

    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserResponse> update(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return Result.success(avatarService.updateAvatar((Long) authentication.getPrincipal(), file));
    }

    @DeleteMapping("/me/avatar")
    public Result<UserResponse> remove(Authentication authentication) {
        return Result.success(avatarService.removeAvatar((Long) authentication.getPrincipal()));
    }

    @GetMapping("/{userId}/avatar")
    public ResponseEntity<byte[]> get(@PathVariable Long userId,
                                      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                      String ifNoneMatch) {
        AvatarResource avatar = avatarService.getAvatar(userId);
        if (avatar.etag().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(avatar.etag())
                    .cacheControl(AVATAR_CACHE)
                    .<byte[]>build();
        }
        return ResponseEntity.ok()
                .eTag(avatar.etag())
                .cacheControl(AVATAR_CACHE)
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(avatar.content().length)
                .body(avatar.content());
    }
}

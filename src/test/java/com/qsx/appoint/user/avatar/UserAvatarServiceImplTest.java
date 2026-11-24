package com.qsx.appoint.user.avatar;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.user.dto.UserResponse;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import com.qsx.appoint.user.service.UserService;
import com.qsx.appoint.user.service.impl.UserAvatarServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 头像替换的补偿顺序、旧对象清理和公开读取结果。 */
@ExtendWith(MockitoExtension.class)
class UserAvatarServiceImplTest {
    @Mock UserMapper userMapper;
    @Mock AvatarStorage avatarStorage;
    @Mock AvatarImageValidator validator;
    @Mock UserService userService;
    UserAvatarServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new UserAvatarServiceImpl(userMapper, avatarStorage, validator, userService, clock);
    }

    @Test
    void updateStoresNewObjectUpdatesDatabaseThenDeletesOldObject() {
        User current = user(7L, "avatars/7/old.png");
        MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});
        ValidatedAvatar validated = new ValidatedAvatar(new byte[]{1}, "image/png", "png");
        when(userMapper.selectById(7L)).thenReturn(current);
        when(validator.validate(file)).thenReturn(validated);
        when(userMapper.updateAvatar(eq(7L), anyString(), eq("image/png"), any())).thenReturn(1);
        when(userService.getCurrentUser(7L)).thenReturn(response("/api/v1/users/7/avatar?v=new.png"));

        UserResponse result = service.updateAvatar(7L, file);

        ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
        verify(avatarStorage).put(newKey.capture(), any(byte[].class), any(String.class));
        assertThat(newKey.getValue()).startsWith("avatars/7/").endsWith(".png");
        verify(avatarStorage).delete("avatars/7/old.png");
        assertThat(result.getAvatarUrl()).isEqualTo("/api/v1/users/7/avatar?v=new.png");
    }

    @Test
    void updateDeletesNewObjectWhenDatabaseUpdateFails() {
        User current = user(7L, "avatars/7/old.png");
        MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});
        ValidatedAvatar validated = new ValidatedAvatar(new byte[]{1}, "image/png", "png");
        when(userMapper.selectById(7L)).thenReturn(current);
        when(validator.validate(file)).thenReturn(validated);
        when(userMapper.updateAvatar(eq(7L), anyString(), eq("image/png"), any()))
                .thenThrow(new IllegalStateException("数据库不可用"));

        assertThatThrownBy(() -> service.updateAvatar(7L, file))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
        verify(avatarStorage).put(newKey.capture(), any(byte[].class), any(String.class));
        verify(avatarStorage).delete(newKey.getValue());
        verify(avatarStorage, never()).delete("avatars/7/old.png");
        verify(userService, never()).getCurrentUser(any());
    }

    @Test
    void removeClearsAvatarAndDeletesCurrentObject() {
        User current = user(7L, "avatars/7/old.png");
        when(userMapper.selectById(7L)).thenReturn(current);
        when(userMapper.clearAvatar(eq(7L), any())).thenReturn(1);
        when(userService.getCurrentUser(7L)).thenReturn(response(null));

        UserResponse result = service.removeAvatar(7L);

        verify(userMapper).clearAvatar(eq(7L), any());
        verify(avatarStorage).delete("avatars/7/old.png");
        assertThat(result.getAvatarUrl()).isNull();
    }

    @Test
    void removeWithoutAvatarIsIdempotent() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, null));
        when(userService.getCurrentUser(7L)).thenReturn(response(null));

        service.removeAvatar(7L);

        verify(userMapper, never()).clearAvatar(any(), any());
        verify(avatarStorage, never()).delete(any());
    }

    @Test
    void getReturnsStoredBytesAndFileNameEtag() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "avatars/7/current.jpg"));
        when(avatarStorage.get("avatars/7/current.jpg"))
                .thenReturn(Optional.of(new AvatarObject(new byte[]{9}, "image/jpeg")));

        AvatarResource result = service.getAvatar(7L);

        assertThat(result.content()).isEqualTo(new byte[]{9});
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.etag()).isEqualTo("\"current.jpg\"");
    }

    @Test
    void getWithoutUserOrAvatarIsNotFound() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, null));

        assertNotFound(() -> service.getAvatar(7L));
    }

    private User user(Long id, String avatarKey) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setAvatarKey(avatarKey);
        user.setAvatarContentType(avatarKey == null ? null : "image/png");
        return user;
    }

    private UserResponse response(String avatarUrl) {
        return new UserResponse(7L, "user@example.com", List.of("USER"), avatarUrl);
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}

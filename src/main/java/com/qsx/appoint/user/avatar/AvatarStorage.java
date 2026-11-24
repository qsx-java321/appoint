package com.qsx.appoint.user.avatar;

import java.util.Optional;

/** 头像对象的存储边界，业务层不依赖具体对象存储实现。 */
public interface AvatarStorage {
    void put(String key, byte[] content, String contentType);

    Optional<AvatarObject> get(String key);

    void delete(String key);
}

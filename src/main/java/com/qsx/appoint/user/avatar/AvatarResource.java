package com.qsx.appoint.user.avatar;

/** 头像读取结果，ETag 使用对象文件名，避免暴露对象存储内部 ETag。 */
public record AvatarResource(byte[] content, String contentType, String etag) {
}

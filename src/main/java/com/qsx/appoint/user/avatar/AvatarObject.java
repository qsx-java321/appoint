package com.qsx.appoint.user.avatar;

/** 从对象存储读取到的头像内容和 MIME 类型。 */
public record AvatarObject(byte[] content, String contentType) {
}

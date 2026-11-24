package com.qsx.appoint.user.avatar;

/** 通过文件头校验后的头像内容，扩展名由服务端判定而不是采用客户端文件名。 */
public record ValidatedAvatar(byte[] content, String contentType, String extension) {
}

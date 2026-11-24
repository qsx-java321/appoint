package com.qsx.appoint.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 数据库中的用户记录，密码只在认证服务内部使用，绝不直接返回接口。 */
@Getter
@Setter
@TableName("users")
public class User {
    private Long id;
    private String email;
    private String passwordHash;
    private String avatarKey;
    private String avatarContentType;
    private LocalDateTime avatarUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

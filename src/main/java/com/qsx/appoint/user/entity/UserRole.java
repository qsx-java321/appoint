package com.qsx.appoint.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 用户与角色的多对多关联记录。 */
@Getter
@Setter
@TableName("user_roles")
public class UserRole {
    private Long id;
    private Long userId;
    private Long roleId;
}

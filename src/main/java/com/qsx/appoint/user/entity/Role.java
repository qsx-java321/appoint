package com.qsx.appoint.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 用户角色，第一阶段仅有 USER 和 ADMIN 两种系统预置角色。 */
@Getter
@Setter
@TableName("roles")
public class Role {
    private Long id;
    private String code;
    private String name;
}

package com.qsx.appoint.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 系统预置 USER、ADMIN、DOCTOR 角色。 */
@Getter
@Setter
@TableName("roles")
public class Role {
    private Long id;
    private String code;
    private String name;
}

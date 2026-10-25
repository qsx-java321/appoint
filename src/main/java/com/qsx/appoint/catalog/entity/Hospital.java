package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 医院基础信息，由 Flyway 提供演示数据。 */
@Getter @Setter @TableName("hospitals")
public class Hospital {
    private Long id;
    private String name;
    private String address;
}

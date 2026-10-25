package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 医院下属科室。 */
@Getter @Setter @TableName("departments")
public class Department {
    private Long id;
    private Long hospitalId;
    private String name;
}

package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 可被预约的医生资料。 */
@Getter @Setter @TableName("doctors")
public class Doctor {
    private Long id;
    private Long userId;
    private String doctorNo;
    private Long departmentId;
    private String name;
    private String title;
    private String introduction;
}

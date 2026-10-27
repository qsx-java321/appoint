package com.qsx.appoint.patient.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 登录用户管理的就诊人资料，可被多张预约引用。 */
@Getter
@Setter
@TableName("patients")
public class Patient {
    private Long id;
    private Long userId;
    private String name;
    private String documentType;
    private String documentNo;
    private String contactPhone;
    private String contactEmail;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

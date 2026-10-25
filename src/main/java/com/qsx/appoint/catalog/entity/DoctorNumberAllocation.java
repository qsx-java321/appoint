package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 医生编号的数据库发号记录；已消耗的序号不会回收。 */
@Getter
@Setter
@TableName("doctor_number_allocations")
public class DoctorNumberAllocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String allocationToken;
}

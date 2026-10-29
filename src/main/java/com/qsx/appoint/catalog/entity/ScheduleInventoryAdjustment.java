package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 管理员对排班总名额的单次审计记录。 */
@Getter
@Setter
@TableName("schedule_inventory_adjustments")
public class ScheduleInventoryAdjustment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scheduleId;
    private Long operatorUserId;
    private Integer delta;
    private String reason;
    private Integer totalCapacityAfter;
    private Integer availableCapacityAfter;
    private LocalDateTime createdAt;
}

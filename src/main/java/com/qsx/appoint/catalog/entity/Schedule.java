package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/** 单个号源的状态只会按 AVAILABLE、HELD、BOOKED 顺序前进。 */
@Getter @Setter @TableName("schedules")
public class Schedule {
    private Long id;
    private Long doctorId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
}

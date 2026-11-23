package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;

/** 一场医生固定时段场次及其统一库存；场次只由首次下单按统一设置生成。 */
@Getter @Setter @TableName("schedules")
public class Schedule {
    private Long id;
    private Long doctorId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String sessionType;
    private Integer totalCapacity;
    private Integer availableCapacity;
    private Integer heldCapacity;
    private Integer confirmedCapacity;
    private Integer nextVisitNumber;
    private BigDecimal registrationFee;
    private String feeCurrency;
}

package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;

/** 一场医生上午或下午排班及其统一库存；sessionType 为空的是只读旧号源。 */
@Getter @Setter @TableName("schedules")
public class Schedule {
    private Long id;
    private Long doctorId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String sessionType;
    private Integer totalCapacity;
    private Integer availableCapacity;
    private Integer heldCapacity;
    private Integer confirmedCapacity;
    private Integer nextVisitNumber;
    private BigDecimal registrationFee;
    private String feeCurrency;
    private java.time.LocalDateTime closedAt;
    private Long closedBy;
}

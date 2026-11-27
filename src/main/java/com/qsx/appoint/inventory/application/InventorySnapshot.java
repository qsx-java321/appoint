package com.qsx.appoint.inventory.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 库存端口返回的不可变场次快照。
 *
 * <p>只包含预约创建、锁后复验和就诊序号分配需要的字段，不暴露容量计数或持久化实体。
 */
public record InventorySnapshot(
        Long scheduleId,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal registrationFee,
        String feeCurrency,
        Integer nextVisitNumber) { }

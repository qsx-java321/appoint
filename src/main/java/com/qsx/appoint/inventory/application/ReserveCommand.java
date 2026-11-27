package com.qsx.appoint.inventory.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 场次库存占号命令。
 *
 * <p>{@code checkedAt} 由调用方在业务入口采样，库存实现不自行读取系统时钟；
 * {@code sessionType} 使用四种场次名称，由实现内部归一化。
 */
public record ReserveCommand(
        Long doctorId,
        LocalDate appointmentDate,
        String sessionType,
        LocalDateTime checkedAt) { }

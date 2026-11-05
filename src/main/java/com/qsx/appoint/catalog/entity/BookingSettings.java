package com.qsx.appoint.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("booking_settings")
public class BookingSettings {
    private Short id;
    private Integer capacityPerSession;
    private BigDecimal registrationFee;
    private String currency;
    private LocalDateTime updatedAt;
}

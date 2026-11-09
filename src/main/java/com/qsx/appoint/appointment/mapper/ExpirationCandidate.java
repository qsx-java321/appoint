package com.qsx.appoint.appointment.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 过期扫描的只读最小候选，仅用于定位和截止+ID游标，不授权直接释放库存。 */
@Getter
@Setter
public class ExpirationCandidate {
    /** 候选预约单ID，处理时重新锁定检查是否仍待支付。 */
    private Long id;
    /** 候选支付截止，用于稳定的截止时间+ID游标，不作为锁后决策事实。 */
    private LocalDateTime paymentExpiresAt;
}

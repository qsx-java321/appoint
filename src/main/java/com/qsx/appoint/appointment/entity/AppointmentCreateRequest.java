package com.qsx.appoint.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 同库创建幂等登记；事务中先登记，预约与支付完成后写回关联及首次回执。
 * <p>只在整个创建事务成功后对其他请求可见；重放使用预约标识而非旧响应中的状态。
 */
@Getter
@Setter
@TableName("appointment_create_requests")
public class AppointmentCreateRequest {
    /** 本地记录主键。 */
    private Long id;
    /** 操作并拥有该记录的账号用户ID，区别于就诊人ID。 */
    private Long userId;
    /** 账号范围内规范化UUID请求键；同一意图重试必须沿用。 */
    private String idempotencyKey;
    /** 规范化预约内容摘要，同键异内容据此拒绝。 */
    private String requestHash;
    /** 关联预约单ID，区别于支付单ID。 */
    private Long appointmentOrderId;
    /** 首次创建响应JSON，完成前可为空；不当作重放时的最新业务状态。 */
    private String responseJson;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private LocalDateTime createdAt;
}

package com.qsx.appoint.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 有效预约占用的数据库唯一声明，覆盖同场次就诊人及跨账号规范化证件。
 * <p>待支付和已确认均保留；取消/过期时随库存释放同事务删除，不作为永久历史凭证。
 */
@Getter
@Setter
@TableName("appointment_active_claims")
public class AppointmentActiveClaim {
    /** 本地记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 内部预约场次ID；新预约请求使用医生/日期/场次类型定位。 */
    private Long scheduleId;
    /** 关联预约单ID，区别于支付单ID。 */
    private Long appointmentOrderId;
    /** 就诊人ID，不等同于账号用户。 */
    private Long patientId;
    /** 规范化证件类型，参与同场次跨账号证件唯一约束。 */
    private String documentType;
    /** 规范化证件号，参与有效占用唯一约束；属于敏感数据。 */
    private String documentNo;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private LocalDateTime createdAt;
}

package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;

/**
 * 本人预约详情及列表元素，组合历史凭证与当前支付结果。
 * <p>操作标记是展示提示，写入口仍复验时间与状态；历史合法组合允许workflow为null。
 */
@Getter @AllArgsConstructor
public class AppointmentResponse {
    /** 预约单ID。 */
    private final Long id;
    /** 预约业务编号，用于凭证识别。 */
    private final String appointmentNo;
    /** 内部预约场次ID；新预约请求使用医生/日期/场次类型定位。 */
    private final Long scheduleId;
    /** 就诊日期，按Asia/Shanghai业务日期解释。 */
    private final LocalDate appointmentDate;
    /** 凭证场次开始时间，不是精确到人的就诊时间。 */
    private final LocalTime startTime;
    /** 凭证场次结束时间。 */
    private final LocalTime endTime;
    /** 预约生命周期状态，区别于paymentStatus和workflowStatus。 */
    private final String status;
    /** 场次内就诊序号，未确认时可为null；取消后不复用。 */
    private final Integer visitNumber;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private final LocalDateTime createdAt;
    /** 关联支付单ID，区别于预约单ID。 */
    private final Long paymentOrderId;
    /** 支付单稳定状态，不等同预约status或展示workflowStatus。 */
    private final String paymentStatus;
    /** 预约+支付组合展示状态；部分历史关闭未付组合为null。 */
    private final String workflowStatus;
    /** 金额，单位元；与currency一起解释，不按分存储。 */
    private final BigDecimal amount;
    /** 金额币种稳定代码，当前新流程使用CNY。 */
    private final String currency;
    private final LocalDateTime paidAt;
    /** 最晚可支付时刻，包含该时刻。 */
    private final LocalDateTime paymentExpiresAt;
    /** 预约或支付关闭时间，尚未关闭可为null。 */
    private final LocalDateTime closedAt;
    /** 本次查询统一的秒精度服务端时间，列表各行共享。 */
    private final LocalDateTime serverTime;
    /** 是否已过期或待支付且超过最晚支付时刻，不依赖扫描是否已更新终态。 */
    private final boolean paymentExpired;
    /** 仅未到期的待支付订单可付款。 */
    private final boolean canPay;
    /** 仅未到期的待支付订单可取消。 */
    private final boolean canCancel;
    /** 创建时就诊人凭证，证件号已经脱敏。 */
    private final AppointmentPatientResponse patient;
    /** 创建时医生、科室和医院凭证，不随目录更新。 */
    private final AppointmentDoctorResponse doctor;
    /** 关联场次类型，历史记录可为空或旧类型。 */
    private final String sessionType;
    /** 已确认预约的就诊进度，其余预约为空。 */
    private final AppointmentVisitResponse visit;
}

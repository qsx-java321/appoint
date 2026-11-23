package com.qsx.appoint.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;

/**
 * 预约持久化模型，保存账号、场次、不可变凭证和预约生命周期。
 * <p>资料及费用取创建快照，状态/序号等随生命周期更新；不作为跨模块interface返回。
 */
@Getter @Setter @TableName("appointment_orders")
public class AppointmentOrder {
    /** 本地记录主键。 */
    private Long id;
    /** 预约业务编号，用于凭证识别。 */
    private String appointmentNo;
    /** 操作并拥有该记录的账号用户ID，区别于就诊人ID。 */
    private Long userId;
    /** 内部预约场次ID；新预约请求使用医生/日期/场次类型定位。 */
    private Long scheduleId;
    /** 就诊人ID，不等同于账号用户。 */
    private Long patientId;
    /** 创建时就诊人姓名快照。 */
    private String patientName;
    /** 创建时证件类型快照。 */
    private String patientDocumentType;
    /** 创建时规范化证件号快照，敏感值不得直接输出到日志或详情。 */
    private String patientDocumentNo;
    /** 创建时联系电话快照。 */
    private String patientContactPhone;
    /** 创建时联系邮箱快照。 */
    private String patientContactEmail;
    /** 预约凭证关联的医生ID。 */
    private Long doctorId;
    /** 创建时医生编号快照。 */
    private String doctorNo;
    /** 创建时医生姓名快照。 */
    private String doctorName;
    /** 创建时医生职称快照。 */
    private String doctorTitle;
    /** 预约凭证关联的科室ID。 */
    private Long departmentId;
    /** 创建时科室名称快照。 */
    private String departmentName;
    /** 预约凭证关联的医院ID。 */
    private Long hospitalId;
    /** 创建时医院名称快照。 */
    private String hospitalName;
    /** 创建时医院地址快照。 */
    private String hospitalAddress;
    /** 就诊日期，按Asia/Shanghai业务日期解释。 */
    private LocalDate appointmentDate;
    /** 凭证场次开始时间，不是精确到人的就诊时间。 */
    private LocalTime startTime;
    /** 凭证场次结束时间。 */
    private LocalTime endTime;
    /** 预约状态字符串，模块入口显式映射AppointmentStatus。 */
    private String status;
    /** 场次内就诊序号，未确认时可为null；取消后不复用。 */
    private Integer visitNumber;
    /** 持久化支付截止；等于截止即失去及时支付资格，扫描延迟不延长。 */
    private LocalDateTime paymentExpiresAt;
    /** 预约创建时挂号费快照，单位元；不随统一设置变化。 */
    private BigDecimal feeAmount;
    /** 预约费用快照币种，当前CNY。 */
    private String feeCurrency;
    /** 预约或支付关闭时间，尚未关闭可为null。 */
    private LocalDateTime closedAt;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private LocalDateTime createdAt;
    /** 最近一次本地状态或记录更新时间。 */
    private LocalDateTime updatedAt;
}

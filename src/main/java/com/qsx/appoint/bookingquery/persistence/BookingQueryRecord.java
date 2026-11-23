package com.qsx.appoint.bookingquery.persistence;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 只属于bookingquery的跨表只读组合行，不是写模块Entity或写入命令。
 * <p>资料字段来自预约创建快照，支付结果来自联查；LEFT JOIN缺失可产生null，
 * 归属和资金完整性由查询用例/Assembler依次检查。
 */
@Getter
@Setter
public class BookingQueryRecord {
    private String sessionType;
    private Long visitRecordId;
    private String visitStatus;
    private LocalDateTime checkedInAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    /** 本地记录主键。 */
    private Long id;
    /** 预约业务编号，用于凭证识别。 */
    private String appointmentNo;
    /** 预约归属账号，用例先据此检查查询权限。 */
    private Long appointmentUserId;
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
    /** 预约原状态，不是支付状态或用户可见workflow。 */
    private String status;
    /** 场次内就诊序号，未确认时可为null；取消后不复用。 */
    private Integer visitNumber;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private LocalDateTime createdAt;
    /** 持久化的最晚支付时刻，包含当秒。 */
    private LocalDateTime paymentExpiresAt;
    /** 预约或支付关闭时间，尚未关闭可为null。 */
    private LocalDateTime closedAt;
    /** 关联支付单ID，区别于预约单ID。 */
    private Long paymentOrderId;
    /** 联查支付归属账号，必须与预约账号一致。 */
    private Long paymentUserId;
    /** 支付单稳定状态，不等同预约status或展示workflowStatus。 */
    private String paymentStatus;
    /** 金额，单位元；与currency一起解释，不按分存储。 */
    private BigDecimal amount;
    /** 金额币种稳定代码，当前新流程使用CNY。 */
    private String currency;
    /** 已支付时间，尚未付款时为 null。 */
    private LocalDateTime paidAt;
}

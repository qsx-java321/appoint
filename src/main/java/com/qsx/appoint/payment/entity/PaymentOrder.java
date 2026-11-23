package com.qsx.appoint.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 与预约一一对应的资金总账事实，记录未付、已付和关闭状态。
 * <p>当前模拟支付只同步写入这三种状态，Entity 不暴露给协调层。
 */
@Getter @Setter @TableName("payment_orders")
public class PaymentOrder {
    /** 本地记录主键。 */
    private Long id;
    /** 本地支付业务编号。 */
    private String paymentNo;
    /** 关联预约单ID，区别于支付单ID。 */
    private Long appointmentOrderId;
    /** 操作并拥有该记录的账号用户ID，区别于就诊人ID。 */
    private Long userId;
    /** 资金整体状态，使用PaymentOrderStatus的稳定存储名称。 */
    private String status;
    /** 应付金额快照，单位元。 */
    private BigDecimal amount;
    /** 金额币种稳定代码，当前为CNY。 */
    private String currency;
    /** 支付截止，与对应预约截止保持一致。 */
    private LocalDateTime expiresAt;
    /** 本系统确认收款的服务端时间，尚未收款时为null。 */
    private LocalDateTime paidAt;
    /** 预约或支付关闭时间，尚未关闭时可为null。 */
    private LocalDateTime closedAt;
    /** 本地创建时间，业务时钟采用Asia/Shanghai。 */
    private LocalDateTime createdAt;
    /** 最近一次本地状态或记录更新时间。 */
    private LocalDateTime updatedAt;
}

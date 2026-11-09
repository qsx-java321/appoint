package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

/**
 * 支付父记录持久化，资金状态以旧状态条件更新防止覆盖并发事实。
 * <p>所有子记录写入前由应用层先锁payment；Mapper不直接访问预约或调用Gateway。
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
    /**
     * 按支付ID普通定位，不获取行锁。
     * @return 支付实体或null；写入前仍需父锁和锁后复验
     */
    @Select("SELECT * FROM payment_orders WHERE id = #{id}")
    PaymentOrder selectByIdForLookup(@Param("id") Long id);

    /**
     * 锁定支付父记录，在锁attempt/refund/notification之前调用。
     * @return 当前支付实体，不存在为null
     */
    @Select("SELECT * FROM payment_orders WHERE id = #{id} FOR UPDATE")
    PaymentOrder selectByIdForUpdate(@Param("id") Long id);

    /**
     * 按预约唯一关联锁支付父记录，供协调事务在锁预约之后使用。
     * @return 支付实体，无关联为null
     */
    @Select("SELECT * FROM payment_orders WHERE appointment_order_id = #{appointmentOrderId} FOR UPDATE")
    PaymentOrder selectByAppointmentOrderIdForUpdate(@Param("appointmentOrderId") Long appointmentOrderId);

    /**
     * 仅UNPAID/PAYING→CLOSED，保留attempt状态且不调用渠道。
     * @return 影响行数，生命周期要求1
     */
    @Update("UPDATE payment_orders SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status IN ('UNPAID', 'PAYING')")
    int closeIfUnpaid(@Param("id") Long id);

    /**
     * 仅PAID→REFUND_PENDING，与唯一退款单及RefundRequested Outbox同事务。
     * @return 影响行数，退款申请用例要求1
     */
    @Update("UPDATE payment_orders SET status = 'REFUND_PENDING', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PAID'")
    int requestRefundIfPaid(@Param("id") Long id);

    /**
     * 仅REFUND_PENDING→REFUNDED，渠道成功后与退款成功和结果事件同事务。
     * @param refundedAt 本系统记录退款完成的时间
     * @return 影响行数，worker要求1
     */
    @Update("UPDATE payment_orders SET status = 'REFUNDED', refunded_at = #{refundedAt}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'REFUND_PENDING'")
    int completeRefund(@Param("id") Long id, @Param("refundedAt") java.time.LocalDateTime refundedAt);

    /**
     * 仅UNPAID→PAYING，与新增CREATED尝试同事务。
     * @return 条件更新行数，T1要求1
     */
    @Update("UPDATE payment_orders SET status = 'PAYING', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'UNPAID'")
    int startPayingIfUnpaid(@Param("id") Long id);

    /**
     * 仅PAYING→UNPAID，不覆盖并发PAID或CLOSED。
     * @return 影响行数；T2失败允许0以保留已推进状态，通知失败路径要求1
     */
    @Update("UPDATE payment_orders SET status = 'UNPAID', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PAYING'")
    int restoreUnpaidIfPaying(@Param("id") Long id);

    /**
     * 仅PAYING→PAID，保存本系统收款确认时间。
     * @param paidAt 锁后采样的服务端确认时间，不采用渠道上报时间
     * @return 影响行数，成功通知要求1
     */
    @Update("UPDATE payment_orders SET status = 'PAID', paid_at = #{paidAt}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PAYING'")
    int payIfPaying(@Param("id") Long id, @Param("paidAt") java.time.LocalDateTime paidAt);

    /**
     * 把CLOSED的实际迟到收款记为PAID，随后同事务申请全额退款。
     * <p>资金事实不能因订单已关闭而丢弃，不代表恢复预约。
     * @return 影响行数，迟到通知要求1
     */
    @Update("UPDATE payment_orders SET status = 'PAID', paid_at = #{paidAt}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'CLOSED'")
    int recordLatePaymentIfClosed(@Param("id") Long id, @Param("paidAt") java.time.LocalDateTime paidAt);
}

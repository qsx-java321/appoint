package com.qsx.appoint.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 预约写模型持久化入口，包含条件迁移、普通定位、行锁及扫描候选读取。
 * <p>Mapper不自行开启事务；FOR UPDATE须在外层事务生效，生命周期按场次→预约顺序使用。
 * 更新返回影响行数，完整用例负责检查并回滚，不能把0行当成已完成全部业务。
 */
@Mapper
public interface AppointmentOrderMapper extends BaseMapper<AppointmentOrder> {
    /**
     * 条件迁移PENDING_PAYMENT→CONFIRMED并保存场次内序号。
     * @param id 预约单ID
     * @param visitNumber 预约模块在已锁场次内分配的序号
     * @return 命中行数，完整确认操作要求1，并与库存确认同事务
     */
    @Update("UPDATE appointment_orders SET status = 'CONFIRMED', visit_number = #{visitNumber}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PENDING_PAYMENT'")
    int confirmIfPending(@Param("id") Long id, @Param("visitNumber") Integer visitNumber);

    /**
     * 普通读取预约供定位场次或查询事实，不持有行锁。
     * @param id 预约单ID
     * @return 预约Entity，不存在为null；不能用此结果代替锁后复验
     */
    @Select("SELECT * FROM appointment_orders WHERE id = #{id}")
    AppointmentOrder selectByIdForLookup(@Param("id") Long id);

    /**
     * 锁定预约行，调用前必须已按协议锁定场次。
     * @param id 预约单ID
     * @return 锁后预约，不存在为null
     */
    @Select("SELECT * FROM appointment_orders WHERE id = #{id} FOR UPDATE")
    AppointmentOrder selectByIdForUpdate(@Param("id") Long id);

    /**
     * 按给定终态条件关闭待支付预约，库存释放和占用删除由完整生命周期操作负责。
     * @param id 预约单ID
     * @param status 可信关闭原因映射出的CANCELLED或EXPIRED，非客户端任意状态
     * @param closedAt 本次业务关闭时间
     * @return 命中行数，调用方要求1
     */
    @Update("""
            UPDATE appointment_orders
            SET status = #{status}, closed_at = #{closedAt}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int closeIfPending(@Param("id") Long id, @Param("status") String status,
                       @Param("closedAt") java.time.LocalDateTime closedAt);

    /**
     * 以固定扫描时间和截止+ID游标分批读取待支付到期候选，不加锁。
     * @param scanTime 本轮固定上界，严格晚于最晚支付时刻才列入
     * @param cursorExpiresAt 上批最后截止，首批为null
     * @param cursorId 同截止下的最后ID，与cursorExpiresAt配套
     * @param limit 批大小上限
     * @return 按截止、ID升序的候选；业务处理必须另开逐单锁定事务
     */
    @Select("""
            <script>
            SELECT id, payment_expires_at
            FROM appointment_orders
            WHERE status = 'PENDING_PAYMENT'
              AND payment_expires_at &lt; #{scanTime}
            <if test="cursorExpiresAt != null">
              AND (payment_expires_at &gt; #{cursorExpiresAt}
                   OR (payment_expires_at = #{cursorExpiresAt} AND id &gt; #{cursorId}))
            </if>
            ORDER BY payment_expires_at, id
            LIMIT #{limit}
            </script>
            """)
    List<ExpirationCandidate> selectExpirationCandidates(
            @Param("scanTime") java.time.LocalDateTime scanTime,
            @Param("cursorExpiresAt") java.time.LocalDateTime cursorExpiresAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}

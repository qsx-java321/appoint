package com.qsx.appoint.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.appointment.entity.AppointmentCreateRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 账号范围内预约创建幂等登记的持久化；登记与完成必须跟随创建事务。 */
@Mapper
public interface AppointmentCreateRequestMapper extends BaseMapper<AppointmentCreateRequest> {
    /**
     * 按账号+规范化UUID查询幂等登记，不混用其他账号的相同键。
     * @return 登记实体，无记录为null
     */
    @Select("SELECT * FROM appointment_create_requests WHERE user_id = #{userId} AND idempotency_key = #{key}")
    AppointmentCreateRequest selectByUserAndKey(@Param("userId") Long userId, @Param("key") String key);

    /**
     * 仅对尚未关联预约的登记写入预约ID和首次响应。
     * @param responseJson 已成功创建的首次响应JSON
     * @return 命中行数，创建事务要求1，否则回滚
     */
    @Update("""
            UPDATE appointment_create_requests
            SET appointment_order_id = #{appointmentOrderId}, response_json = #{responseJson}
            WHERE id = #{id} AND appointment_order_id IS NULL
            """)
    int complete(@Param("id") Long id, @Param("appointmentOrderId") Long appointmentOrderId,
                 @Param("responseJson") String responseJson);
}

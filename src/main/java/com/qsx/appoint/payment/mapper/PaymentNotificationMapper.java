package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 通知幂等审计查询，channel+notificationId唯一范围与payloadHash复验配合使用。
 * <p>普通读负责快速短路，锁下读在payment/attempt之后复验并发已提交审计。
 */
@Mapper
public interface PaymentNotificationMapper extends BaseMapper<PaymentNotification> {
    /**
     * 按渠道和通知ID普通读取已保存审计，供快速幂等短路。
     * @return 原审计或null；存在仍需调用方比较payloadHash
     */
    @Select("SELECT * FROM payment_notifications WHERE channel = #{channel} AND notification_id = #{notificationId}")
    PaymentNotification selectByChannelAndNotificationId(@Param("channel") String channel,
                                                         @Param("notificationId") String notificationId);

    /**
     * 父支付及attempt锁后复查通知审计，处理等待锁期间的并发提交。
     * @return 已保存审计或null；无行时仍由唯一键保护插入竞争
     */
    @Select("SELECT * FROM payment_notifications WHERE channel = #{channel} AND notification_id = #{notificationId} FOR UPDATE")
    PaymentNotification selectByChannelAndNotificationIdForUpdate(@Param("channel") String channel,
                                                                  @Param("notificationId") String notificationId);
}


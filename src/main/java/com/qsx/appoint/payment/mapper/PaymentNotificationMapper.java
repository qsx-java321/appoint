package com.qsx.appoint.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.payment.entity.PaymentNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentNotificationMapper extends BaseMapper<PaymentNotification> {
    @Select("SELECT * FROM payment_notifications WHERE channel = #{channel} AND notification_id = #{notificationId}")
    PaymentNotification selectByChannelAndNotificationId(@Param("channel") String channel,
                                                         @Param("notificationId") String notificationId);

    @Select("SELECT * FROM payment_notifications WHERE channel = #{channel} AND notification_id = #{notificationId} FOR UPDATE")
    PaymentNotification selectByChannelAndNotificationIdForUpdate(@Param("channel") String channel,
                                                                  @Param("notificationId") String notificationId);
}


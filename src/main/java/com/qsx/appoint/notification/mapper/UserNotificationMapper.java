package com.qsx.appoint.notification.mapper;

import com.qsx.appoint.notification.entity.UserNotification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 消息中心读写入口；重复事件和重复业务节点都通过唯一键幂等忽略。
 */
@Mapper
public interface UserNotificationMapper {
    String COLUMNS = "id, event_id, user_id, appointment_order_id, notification_type, title, patient_name, "
            + "occurred_at, detail, created_at";

    @Insert("""
            INSERT INTO user_notifications
                (event_id, user_id, appointment_order_id, notification_type, title, patient_name,
                 occurred_at, detail, created_at)
            VALUES
                (#{eventId}, #{userId}, #{appointmentOrderId}, #{notificationType}, #{title}, #{patientName},
                 #{occurredAt}, #{detail}, #{createdAt})
            ON DUPLICATE KEY UPDATE event_id = event_id
            """)
    int insertIgnore(UserNotification notification);

    @Select("SELECT " + COLUMNS + " FROM user_notifications "
            + "WHERE user_id = #{userId} ORDER BY occurred_at DESC, id DESC")
    List<UserNotification> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT " + COLUMNS + " FROM user_notifications WHERE id = #{id} AND user_id = #{userId}")
    UserNotification selectOwned(@Param("id") Long id, @Param("userId") Long userId);
}

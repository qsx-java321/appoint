package com.qsx.appoint.notification.application;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.notification.dto.NotificationDetailResponse;
import com.qsx.appoint.notification.dto.NotificationListItemResponse;
import com.qsx.appoint.notification.entity.UserNotification;
import com.qsx.appoint.notification.mapper.UserNotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 消息中心查询实现；归属过滤直接进入SQL，不区分不存在和无权读取。 */
@Service
@RequiredArgsConstructor
class DefaultNotificationQueryService implements NotificationQueryService {
    private final UserNotificationMapper notifications;

    @Override
    public List<NotificationListItemResponse> listOwned(Long userId) {
        return notifications.selectByUserId(userId).stream()
                .map(row -> new NotificationListItemResponse(row.getId(), row.getNotificationType(),
                        row.getTitle(), row.getPatientName(), row.getOccurredAt()))
                .toList();
    }

    @Override
    public NotificationDetailResponse getOwned(Long userId, Long notificationId) {
        UserNotification row = notifications.selectOwned(notificationId, userId);
        if (row == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return new NotificationDetailResponse(row.getId(), row.getNotificationType(), row.getTitle(),
                row.getPatientName(), row.getOccurredAt(), row.getDetail(), row.getAppointmentOrderId());
    }
}

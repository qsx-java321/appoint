package com.qsx.appoint.notification.application;

import com.qsx.appoint.notification.dto.NotificationDetailResponse;
import com.qsx.appoint.notification.dto.NotificationListItemResponse;

import java.util.List;

/** 按当前登录账号读取消息中心，不判断角色。 */
public interface NotificationQueryService {
    /** 列表按发生时间、ID倒序；无数据返回空列表。 */
    List<NotificationListItemResponse> listOwned(Long userId);

    /**
     * 读取本人通知详情。
     * @throws com.qsx.appoint.common.BusinessException 通知不存在或不属于当前账号，返回RESOURCE_NOT_FOUND
     */
    NotificationDetailResponse getOwned(Long userId, Long notificationId);
}

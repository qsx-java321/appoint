package com.qsx.appoint.notification.controller;

import com.qsx.appoint.common.Result;
import com.qsx.appoint.notification.application.NotificationQueryService;
import com.qsx.appoint.notification.dto.NotificationDetailResponse;
import com.qsx.appoint.notification.dto.NotificationListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前登录账号的消息中心入口；不判断角色，只读取本人数据。 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationQueryService notifications;

    @GetMapping
    public Result<List<NotificationListItemResponse>> list(Authentication authentication) {
        return Result.success(notifications.listOwned((Long) authentication.getPrincipal()));
    }

    @GetMapping("/{id}")
    public Result<NotificationDetailResponse> get(Authentication authentication, @PathVariable Long id) {
        return Result.success(notifications.getOwned((Long) authentication.getPrincipal(), id));
    }
}

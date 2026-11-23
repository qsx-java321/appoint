package com.qsx.appoint.notification.domain;

/** 站内通知节点；同一预约的同一节点最多生成一条。 */
public enum NotificationType {
    APPOINTMENT_CONFIRMED("预约成功"),
    CHECKED_IN("签到成功"),
    VISIT_STARTED("开始接诊"),
    VISIT_COMPLETED("完成接诊");

    private final String title;

    NotificationType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}

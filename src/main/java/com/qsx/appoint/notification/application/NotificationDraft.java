package com.qsx.appoint.notification.application;

/** 事件生成时已经渲染完成的通知内容，后续不再根据当前资料重新计算。 */
public record NotificationDraft(
        String title,
        String patientName,
        String detail) {
}

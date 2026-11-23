package com.qsx.appoint.notification.application;

import com.qsx.appoint.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 用预约快照渲染固定中文文案；同一事件只渲染一次并保存结果。
 */
@Component
public class NotificationMessageFactory {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M 月 d 日");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 生成通知标题、就诊人和完整详情。
     * @param snapshot 预约单快照
     * @param type 通知节点
     * @param occurredAt 本次业务动作发生时间
     * @return 已渲染的通知内容
     */
    public NotificationDraft create(AppointmentNotificationSnapshot snapshot, NotificationType type,
                                    LocalDateTime occurredAt) {
        if (snapshot == null || type == null || occurredAt == null) {
            throw new IllegalArgumentException("通知快照、类型和发生时间不能为空");
        }
        String detail = switch (type) {
            case APPOINTMENT_CONFIRMED -> confirmed(snapshot);
            case CHECKED_IN -> checkedIn(snapshot, occurredAt);
            case VISIT_STARTED -> visitStarted(snapshot, occurredAt);
            case VISIT_COMPLETED -> visitCompleted(snapshot, occurredAt);
        };
        return new NotificationDraft(type.title(), snapshot.patientName(), detail);
    }

    private String confirmed(AppointmentNotificationSnapshot snapshot) {
        requireVisitNumber(snapshot);
        return snapshot.patientName() + "的预约已确认。"
                + location(snapshot)
                + "，就诊时间为" + date(snapshot.appointmentDate())
                + " " + time(snapshot.startTime()) + "–" + time(snapshot.endTime())
                + "，接诊医生为" + snapshot.doctorName()
                + "，就诊序号为" + snapshot.visitNumber() + "号。";
    }

    private String checkedIn(AppointmentNotificationSnapshot snapshot, LocalDateTime occurredAt) {
        requireVisitNumber(snapshot);
        return snapshot.patientName() + "已完成到院签到，请" + snapshot.departmentName() + "候诊。"
                + location(snapshot)
                + "，就诊序号为" + snapshot.visitNumber() + "号"
                + "，签到时间为" + date(occurredAt.toLocalDate()) + " " + time(occurredAt) + "。";
    }

    private String visitStarted(AppointmentNotificationSnapshot snapshot, LocalDateTime occurredAt) {
        requireVisitNumber(snapshot);
        return snapshot.doctorName() + "已开始为" + snapshot.patientName() + "接诊。"
                + location(snapshot)
                + "，就诊序号为" + snapshot.visitNumber() + "号"
                + "，开始接诊时间为" + date(occurredAt.toLocalDate()) + " " + time(occurredAt) + "。";
    }

    private String visitCompleted(AppointmentNotificationSnapshot snapshot, LocalDateTime occurredAt) {
        return snapshot.patientName() + "本次就诊已完成。"
                + location(snapshot)
                + "，接诊医生为" + snapshot.doctorName()
                + "，完成接诊时间为" + date(occurredAt.toLocalDate()) + " " + time(occurredAt) + "。";
    }

    private String location(AppointmentNotificationSnapshot snapshot) {
        return "就诊地点为" + snapshot.hospitalName() + " · " + snapshot.departmentName();
    }

    private String date(java.time.LocalDate value) {
        if (value == null) throw new IllegalStateException("通知快照缺少就诊日期");
        return DATE.format(value);
    }

    private String time(java.time.LocalTime value) {
        if (value == null) throw new IllegalStateException("通知快照缺少时间");
        return TIME.format(value);
    }

    private String time(LocalDateTime value) {
        return TIME.format(value);
    }

    private void requireVisitNumber(AppointmentNotificationSnapshot snapshot) {
        if (snapshot.visitNumber() == null) {
            throw new IllegalStateException("已确认预约缺少就诊序号");
        }
    }
}

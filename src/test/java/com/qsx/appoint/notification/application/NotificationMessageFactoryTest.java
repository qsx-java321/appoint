package com.qsx.appoint.notification.application;

import com.qsx.appoint.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 四类通知必须与规格中的固定中文文案逐字一致。 */
class NotificationMessageFactoryTest {
    private final NotificationMessageFactory factory = new NotificationMessageFactory();
    private final AppointmentNotificationSnapshot snapshot = new AppointmentNotificationSnapshot(
            31L, 12L, "张三", "示例市第一人民医院", "内科", "张医生",
            LocalDate.of(2026, 11, 13), LocalTime.of(8, 0), LocalTime.of(10, 0), 3);

    @Test
    void appointmentConfirmedUsesSnapshotDateAndVisitNumber() {
        var draft = factory.create(snapshot, NotificationType.APPOINTMENT_CONFIRMED,
                LocalDateTime.parse("2026-11-12T08:00:00"));
        assertThat(draft.title()).isEqualTo("预约成功");
        assertThat(draft.patientName()).isEqualTo("张三");
        assertThat(draft.detail()).isEqualTo(
                "张三的预约已确认。就诊地点为示例市第一人民医院 · 内科，"
                        + "就诊时间为11 月 13 日 08:00–10:00，接诊医生为张医生，就诊序号为3号。");
    }

    @Test
    void checkedInUsesBusinessActionDateAndTime() {
        var draft = factory.create(snapshot, NotificationType.CHECKED_IN,
                LocalDateTime.parse("2026-11-13T07:35:00"));
        assertThat(draft.title()).isEqualTo("签到成功");
        assertThat(draft.detail()).isEqualTo(
                "张三已完成到院签到，请内科候诊。就诊地点为示例市第一人民医院 · 内科，"
                        + "就诊序号为3号，签到时间为11 月 13 日 07:35。");
    }

    @Test
    void visitStartedUsesDoctorAndBusinessActionTime() {
        var draft = factory.create(snapshot, NotificationType.VISIT_STARTED,
                LocalDateTime.parse("2026-11-13T08:15:00"));
        assertThat(draft.title()).isEqualTo("开始接诊");
        assertThat(draft.detail()).isEqualTo(
                "张医生已开始为张三接诊。就诊地点为示例市第一人民医院 · 内科，"
                        + "就诊序号为3号，开始接诊时间为11 月 13 日 08:15。");
    }

    @Test
    void visitCompletedUsesDoctorAndBusinessActionTime() {
        var draft = factory.create(snapshot, NotificationType.VISIT_COMPLETED,
                LocalDateTime.parse("2026-11-13T08:28:00"));
        assertThat(draft.title()).isEqualTo("完成接诊");
        assertThat(draft.detail()).isEqualTo(
                "张三本次就诊已完成。就诊地点为示例市第一人民医院 · 内科，"
                        + "接诊医生为张医生，完成接诊时间为11 月 13 日 08:28。");
    }

    @Test
    void singleDigitMonthAndDayKeepSpecifiedSpacing() {
        var early = new AppointmentNotificationSnapshot(32L, 12L, "李四", "医院", "外科", "李医生",
                LocalDate.of(2027, 1, 5), LocalTime.of(10, 30), LocalTime.of(12, 0), 1);
        var draft = factory.create(early, NotificationType.APPOINTMENT_CONFIRMED,
                LocalDateTime.parse("2027-01-04T09:00:00"));
        assertThat(draft.detail()).contains("就诊时间为1 月 5 日 10:30–12:00");
    }
}

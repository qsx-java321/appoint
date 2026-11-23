package com.qsx.appoint.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.notification.application.NotificationPayload;
import com.qsx.appoint.notification.application.NotificationPayloadCodec;
import com.qsx.appoint.notification.domain.NotificationType;
import com.qsx.appoint.security.JwtTokenProvider;
import com.qsx.appoint.support.AppointmentClockTestConfiguration;
import com.qsx.appoint.support.FormalPaymentTestSupport;
import com.qsx.appoint.support.MutableAppointmentClock;
import com.qsx.appoint.support.RegressionTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 四个业务节点在同一事务中写出一条通知事件，重复操作不制造第二条。 */
@RegressionTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("regression")
@Import({FormalPaymentTestSupport.class, AppointmentClockTestConfiguration.class})
@Transactional
class NotificationFlowIntegrationTest {
    private static final String DATE = "2026-11-13";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired BookingCoordinator bookings;
    @Autowired BookingLifecycleCoordinator lifecycle;
    @Autowired FormalPaymentTestSupport payments;
    @Autowired MutableAppointmentClock clock;
    @Autowired NotificationPayloadCodec codec;

    String owner, doctor, admin;

    @BeforeEach
    void setup() throws Exception {
        clock.set("2026-11-12T08:00:00");
        for (long id = 84001; id <= 84004; id++) {
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,'unused')", id, id + "@notify.example.com");
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='USER'", id);
        }
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT 84002,id FROM roles WHERE code='DOCTOR'");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT 84003,id FROM roles WHERE code='ADMIN'");
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) "
                + "VALUES(84011,84001,'就诊人甲','RESIDENT_ID_CARD','110105199001010010','13800138000','p@example.com',TRUE)");
        owner = token(84001L, "USER");
        doctor = token(84002L, "USER", "DOCTOR");
        admin = token(84003L, "ADMIN");
        mvc.perform(put("/api/v1/admin/doctors/1/account").header("Authorization", admin)
                        .contentType("application/json").content("{\"userId\":\"84002\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void paymentCreatesOneConfirmedEventAndReplayKeepsItSingle() {
        var appointment = create("MORNING_FIRST", true);
        long id = appointment.getAppointmentOrderId();

        List<Map<String, Object>> rows = outbox(id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("notification_type")).isEqualTo("APPOINTMENT_CONFIRMED");
        assertThat(rows.get(0).get("status")).isEqualTo("PENDING");
        assertThat(rows.get(0).get("attempts")).isEqualTo(0);
        NotificationPayload payload = codec.decode((String) rows.get(0).get("payload"));
        assertThat(payload.type()).isEqualTo(NotificationType.APPOINTMENT_CONFIRMED);
        assertThat(payload.appointmentId()).isEqualTo(id);
        assertThat(payload.userId()).isEqualTo(84001L);
        assertThat(payload.title()).isEqualTo("预约成功");
        assertThat(payload.patientName()).isEqualTo("就诊人甲");
        assertThat(payload.occurredAt()).isEqualTo(java.time.LocalDateTime.parse("2026-11-12T08:00:00"));
        assertThat(payload.detail()).isEqualTo(
                "就诊人甲的预约已确认。就诊地点为示例市第一人民医院 · 内科，"
                        + "就诊时间为11 月 13 日 08:00–10:00，接诊医生为张医生，就诊序号为1号。");

        payments.pay(84001L, id);
        assertThat(outbox(id)).hasSize(1);
    }

    @Test
    void checkInStartAndCompleteCreateFourOrderedEventsAndRepeatsAreRejected() throws Exception {
        var appointment = create("MORNING_FIRST", true);
        long id = appointment.getAppointmentOrderId();

        clock.set("2026-11-13T07:35:00");
        action("/appointments/" + id + "/check-in", owner, 200);
        clock.set("2026-11-13T08:15:00");
        action("/doctor/appointments/" + id + "/start-visit", doctor, 200);
        clock.set("2026-11-13T08:28:00");
        action("/doctor/appointments/" + id + "/complete-visit", doctor, 200);

        List<Map<String, Object>> rows = outbox(id);
        assertThat(rows).extracting(row -> row.get("notification_type"))
                .containsExactly("APPOINTMENT_CONFIRMED", "CHECKED_IN", "VISIT_STARTED", "VISIT_COMPLETED");
        List<NotificationPayload> payloads = rows.stream()
                .map(row -> codec.decode((String) row.get("payload"))).toList();
        assertThat(payloads).extracting(NotificationPayload::occurredAt).containsExactly(
                java.time.LocalDateTime.parse("2026-11-12T08:00:00"),
                java.time.LocalDateTime.parse("2026-11-13T07:35:00"),
                java.time.LocalDateTime.parse("2026-11-13T08:15:00"),
                java.time.LocalDateTime.parse("2026-11-13T08:28:00"));
        assertThat(payloads.get(1).detail()).isEqualTo(
                "就诊人甲已完成到院签到，请内科候诊。就诊地点为示例市第一人民医院 · 内科，"
                        + "就诊序号为1号，签到时间为11 月 13 日 07:35。");
        assertThat(payloads.get(2).detail()).isEqualTo(
                "张医生已开始为就诊人甲接诊。就诊地点为示例市第一人民医院 · 内科，"
                        + "就诊序号为1号，开始接诊时间为11 月 13 日 08:15。");
        assertThat(payloads.get(3).detail()).isEqualTo(
                "就诊人甲本次就诊已完成。就诊地点为示例市第一人民医院 · 内科，"
                        + "接诊医生为张医生，完成接诊时间为11 月 13 日 08:28。");

        payments.pay(84001L, id);
        action("/appointments/" + id + "/check-in", owner, 409);
        action("/doctor/appointments/" + id + "/start-visit", doctor, 409);
        action("/doctor/appointments/" + id + "/complete-visit", doctor, 409);
        assertThat(outbox(id)).hasSize(4);

        // 事件生成后修改就诊人和目录，已保存的文案与就诊人快照不变。
        jdbc.update("UPDATE patients SET name='改名后的就诊人' WHERE id=84011");
        jdbc.update("UPDATE doctors SET name='改名后的医生' WHERE id=1");
        jdbc.update("UPDATE departments SET name='改名后的科室' WHERE id=1");
        jdbc.update("UPDATE hospitals SET name='改名后的医院' WHERE id=1");
        List<NotificationPayload> unchanged = outbox(id).stream()
                .map(row -> codec.decode((String) row.get("payload"))).toList();
        assertThat(unchanged).extracting(NotificationPayload::patientName)
                .containsOnly("就诊人甲");
        assertThat(unchanged.get(0).detail()).contains("示例市第一人民医院 · 内科", "张医生", "就诊序号为1号");
    }

    @Test
    void unpaidCancelledAndExpiredAppointmentsDoNotCreateNotifications() {
        var cancelled = create("MORNING_SECOND", false);
        lifecycle.cancelOwned(84001L, cancelled.getAppointmentOrderId());
        var expired = create("AFTERNOON_FIRST", false);
        clock.set("2026-11-12T08:16:00");
        lifecycle.expireIfDue(expired.getAppointmentOrderId());

        assertThat(outbox(cancelled.getAppointmentOrderId())).isEmpty();
        assertThat(outbox(expired.getAppointmentOrderId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox_events", Integer.class)).isZero();
    }

    private CreateAppointmentResponse create(String session, boolean pay) {
        var request = new CreateAppointmentRequest();
        request.setDoctorId(1L);
        request.setPatientId(84011L);
        request.setAppointmentDate(LocalDate.parse(DATE));
        request.setSessionType(session);
        var response = (CreateAppointmentResponse) bookings.create(
                84001L, bookings.issueToken(84001L).token(), request);
        if (pay) payments.pay(84001L, response.getAppointmentOrderId());
        return response;
    }

    private List<Map<String, Object>> outbox(long appointmentId) {
        return jdbc.queryForList(
                "SELECT * FROM notification_outbox_events WHERE appointment_order_id=? ORDER BY id", appointmentId);
    }

    private JsonNode action(String path, String token, int expected) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).header("Authorization", token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).path("data");
    }

    private String token(Long id, String... roles) {
        return "Bearer " + tokens.createToken(id, id + "@notify.example.com", List.of(roles));
    }
}

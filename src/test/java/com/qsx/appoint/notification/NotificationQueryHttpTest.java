package com.qsx.appoint.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.notification.application.NotificationInboxWriter;
import com.qsx.appoint.notification.application.NotificationPayload;
import com.qsx.appoint.notification.domain.NotificationType;
import com.qsx.appoint.security.JwtTokenProvider;
import com.qsx.appoint.support.AppointmentClockTestConfiguration;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 消息中心HTTP契约：倒序、空数组、字段裁剪和账号隔离。 */
@RegressionTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-query;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("regression")
@Import(AppointmentClockTestConfiguration.class)
@Transactional
class NotificationQueryHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired BookingCoordinator bookings;
    @Autowired NotificationInboxWriter inbox;
    @Autowired MutableAppointmentClock clock;

    String owner, other, empty;
    long ownerAppointment, otherAppointment;

    @BeforeEach
    void setup() {
        clock.set("2026-11-12T08:00:00");
        for (long id = 85001; id <= 85003; id++) {
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,'unused')", id, id + "@query.example.com");
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='USER'", id);
        }
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) "
                + "VALUES(85011,85001,'就诊人甲','RESIDENT_ID_CARD','110105199001010010','13800138000','a@example.com',TRUE)");
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) "
                + "VALUES(85012,85002,'就诊人乙','RESIDENT_ID_CARD','110105199001010029','13800138000','b@example.com',TRUE)");
        owner = token(85001L);
        other = token(85002L);
        empty = token(85003L);
        ownerAppointment = create(85001L, 85011L, "MORNING_FIRST");
        otherAppointment = create(85002L, 85012L, "MORNING_SECOND");

        inbox.write(payload("e-a-1", ownerAppointment, 85001L, NotificationType.VISIT_STARTED,
                "开始接诊", "就诊人甲", "2026-11-13T08:00:00", "甲开始接诊详情"));
        inbox.write(payload("e-a-2", ownerAppointment, 85001L, NotificationType.APPOINTMENT_CONFIRMED,
                "预约成功", "就诊人甲", "2026-11-13T09:00:00", "甲预约成功详情"));
        inbox.write(payload("e-a-3", ownerAppointment, 85001L, NotificationType.CHECKED_IN,
                "签到成功", "就诊人甲", "2026-11-13T09:00:00", "甲签到成功详情"));
        inbox.write(payload("e-b-1", otherAppointment, 85002L, NotificationType.APPOINTMENT_CONFIRMED,
                "预约成功", "就诊人乙", "2026-11-13T10:00:00", "乙预约成功详情"));
    }

    @Test
    void listReturnsOwnRowsByOccurredAtThenIdDescendingWithoutDetail() throws Exception {
        var data = getData("/notifications", owner);
        assertThat(data).hasSize(3);
        assertThat(data).extracting(row -> row.path("title").asText())
                .containsExactly("签到成功", "预约成功", "开始接诊");
        assertThat(data.get(0).path("type").asText()).isEqualTo("CHECKED_IN");
        assertThat(data.get(0).path("patientName").asText()).isEqualTo("就诊人甲");
        assertThat(data.get(0).path("occurredAt").asText()).isEqualTo("2026-11-13T09:00:00");
        assertThat(data.get(0).has("detail")).isFalse();
        assertThat(data.get(0).has("appointmentId")).isFalse();
    }

    @Test
    void detailReturnsSavedSnapshotAndAppointmentId() throws Exception {
        long id = idOf(getData("/notifications", owner), "预约成功");
        var detail = getData("/notifications/" + id, owner);
        assertThat(detail.path("title").asText()).isEqualTo("预约成功");
        assertThat(detail.path("type").asText()).isEqualTo("APPOINTMENT_CONFIRMED");
        assertThat(detail.path("patientName").asText()).isEqualTo("就诊人甲");
        assertThat(detail.path("occurredAt").asText()).isEqualTo("2026-11-13T09:00:00");
        assertThat(detail.path("detail").asText()).isEqualTo("甲预约成功详情");
        assertThat(detail.path("appointmentId").asText()).isEqualTo(String.valueOf(ownerAppointment));
    }

    @Test
    void otherAccountCannotReadListOrDetailAndGetsNotFound() throws Exception {
        var otherList = getData("/notifications", other);
        assertThat(otherList).hasSize(1);
        assertThat(otherList.get(0).path("patientName").asText()).isEqualTo("就诊人乙");

        long ownerNotification = idOf(getData("/notifications", owner), "预约成功");
        mvc.perform(get("/api/v1/notifications/" + ownerNotification).header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
        mvc.perform(get("/api/v1/notifications/999999").header("Authorization", owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void emptyListIsArrayAndAuthenticationIsRequired() throws Exception {
        assertThat(getData("/notifications", empty)).isEmpty();
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEventAndDuplicateNodeKeepOnlyFirstNotification() {
        var duplicate = payload("e-a-2", ownerAppointment, 85001L, NotificationType.APPOINTMENT_CONFIRMED,
                "预约成功", "就诊人甲", "2026-11-13T09:00:00", "重复投递详情");
        inbox.write(duplicate);
        inbox.write(payload("e-a-2", otherAppointment, 85002L, NotificationType.VISIT_COMPLETED,
                "完成接诊", "就诊人乙", "2026-11-13T11:00:00", "复用事件ID的详情"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE appointment_order_id=?",
                Integer.class, ownerAppointment)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT detail FROM user_notifications "
                + "WHERE appointment_order_id=? AND notification_type='APPOINTMENT_CONFIRMED'",
                String.class, ownerAppointment)).isEqualTo("甲预约成功详情");
    }

    private NotificationPayload payload(String eventId, long appointmentId, long userId, NotificationType type,
                                        String title, String patientName, String occurredAt, String detail) {
        return new NotificationPayload(eventId, type, appointmentId, userId, title, patientName,
                LocalDateTime.parse(occurredAt), detail);
    }

    private long create(long userId, long patientId, String session) {
        var request = new CreateAppointmentRequest();
        request.setDoctorId(1L);
        request.setPatientId(patientId);
        request.setAppointmentDate(LocalDate.parse("2026-11-13"));
        request.setSessionType(session);
        var response = (CreateAppointmentResponse) bookings.create(
                userId, bookings.issueToken(userId).token(), request);
        return response.getAppointmentOrderId();
    }

    private long idOf(JsonNode rows, String title) {
        for (var row : rows) if (row.path("title").asText().equals(title)) return row.path("id").asLong();
        throw new AssertionError("未找到标题为" + title + "的通知");
    }

    private JsonNode getData(String path, String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1" + path).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private String token(Long id) {
        return "Bearer " + tokens.createToken(id, id + "@query.example.com", List.of("USER"));
    }
}

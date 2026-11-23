package com.qsx.appoint.notification;

import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.notification.application.NotificationPayload;
import com.qsx.appoint.notification.application.NotificationPayloadCodec;
import com.qsx.appoint.notification.application.NotificationOutboxPublisher;
import com.qsx.appoint.notification.domain.NotificationType;
import com.qsx.appoint.notification.messaging.NotificationMessagingConfig;
import com.qsx.appoint.support.CommittedFixtureScope;
import com.qsx.appoint.support.FormalPaymentTestSupport;
import com.qsx.appoint.support.MutableAppointmentClock;
import com.qsx.appoint.support.RabbitItTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** 真实RabbitMQ验证Outbox发布、消费入库、重复投递幂等和死信队列。 */
@RabbitItTest
@Timeout(90)
class NotificationRabbitIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingCoordinator bookings;
    @Autowired FormalPaymentTestSupport payments;
    @Autowired MutableAppointmentClock clock;
    @Autowired NotificationOutboxPublisher publisher;
    @Autowired NotificationPayloadCodec codec;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired AmqpAdmin amqpAdmin;

    CommittedFixtureScope fixture;

    @BeforeEach
    void setup() {
        fixture = new CommittedFixtureScope(jdbc);
        clock.set("2026-11-12T08:00:00");
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(87001,'rabbit@example.com','unused')");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT 87001,id FROM roles WHERE code='USER'");
        jdbc.update("INSERT INTO patients(id,user_id,name,document_type,document_no,contact_phone,contact_email,enabled) "
                + "VALUES(87011,87001,'消息就诊人','RESIDENT_ID_CARD','110105199001010010','13800138000','p@example.com',TRUE)");
    }

    @AfterEach
    void cleanup() {
        fixture.close();
    }

    @Test
    void outboxIsPublishedConsumedAndRendered() {
        long appointmentId = createAndPay();

        publisher.publishPending();
        await(() -> notificationCount(appointmentId) == 1, Duration.ofSeconds(15));

        var row = jdbc.queryForMap("SELECT * FROM user_notifications WHERE appointment_order_id=?", appointmentId);
        assertThat(row.get("notification_type")).isEqualTo("APPOINTMENT_CONFIRMED");
        assertThat(row.get("title")).isEqualTo("预约成功");
        assertThat(row.get("patient_name")).isEqualTo("消息就诊人");
        assertThat(row.get("detail")).isEqualTo(
                "消息就诊人的预约已确认。就诊地点为示例市第一人民医院 · 内科，"
                        + "就诊时间为11 月 13 日 08:00–10:00，接诊医生为张医生，就诊序号为1号。");
        await(() -> "PUBLISHED".equals(jdbc.queryForObject(
                "SELECT status FROM notification_outbox_events WHERE appointment_order_id=?",
                String.class, appointmentId)), Duration.ofSeconds(10));
    }

    @Test
    void duplicateDeliveryInsertsOnlyOneNotification() {
        long appointmentId = createAndPay();
        publisher.publishPending();
        await(() -> notificationCount(appointmentId) == 1, Duration.ofSeconds(15));

        var outbox = jdbc.queryForMap(
                "SELECT event_id, payload FROM notification_outbox_events WHERE appointment_order_id=?", appointmentId);
        String eventId = (String) outbox.get("event_id");
        String payload = (String) outbox.get("payload");
        send(eventId, payload);
        send(eventId, payload);

        // 后发的签到消息作为顺序标记；它入库时，前面的重复消息一定已经处理完。
        String marker = codec.encode(new NotificationPayload("rabbit-marker-" + appointmentId,
                NotificationType.CHECKED_IN, appointmentId, 87001L, "签到成功", "消息就诊人",
                LocalDateTime.parse("2026-11-13T07:35:00"), "顺序标记"));
        send("rabbit-marker-" + appointmentId, marker);
        await(() -> notificationCount(appointmentId) == 2, Duration.ofSeconds(15));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications "
                + "WHERE appointment_order_id=? AND notification_type='APPOINTMENT_CONFIRMED'",
                Integer.class, appointmentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications "
                + "WHERE appointment_order_id=? AND notification_type='CHECKED_IN'",
                Integer.class, appointmentId)).isEqualTo(1);
    }

    @Test
    void invalidMessageIsRetriedThenSentToDeadLetterQueue() {
        long started = System.nanoTime();
        send("invalid-" + System.nanoTime(), "{not-json");

        await(() -> queueCount(NotificationMessagingConfig.DEAD_LETTER_QUEUE) >= 1, Duration.ofSeconds(25));

        // 消费者在单次投递内按1秒、2秒、4秒重试；死信到达时间应覆盖这些退避。
        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isGreaterThanOrEqualTo(Duration.ofSeconds(6));
        assertThat(queueCount(NotificationMessagingConfig.QUEUE)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications", Integer.class)).isZero();
    }

    private long createAndPay() {
        var request = new CreateAppointmentRequest();
        request.setDoctorId(1L);
        request.setPatientId(87011L);
        request.setAppointmentDate(LocalDate.parse("2026-11-13"));
        request.setSessionType("MORNING_FIRST");
        var response = (CreateAppointmentResponse) bookings.create(
                87001L, bookings.issueToken(87001L).token(), request);
        payments.pay(87001L, response.getAppointmentOrderId());
        return response.getAppointmentOrderId();
    }

    private void send(String messageId, String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(messageId);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbitTemplate.send(NotificationMessagingConfig.EXCHANGE, NotificationMessagingConfig.ROUTING_KEY,
                new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
    }

    private int notificationCount(long appointmentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE appointment_order_id=?",
                Integer.class, appointmentId);
    }

    private int queueCount(String queue) {
        var info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }

    private void await(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail("等待RabbitMQ处理时被中断");
            }
        }
        fail("等待RabbitMQ处理超时：" + timeout);
    }
}

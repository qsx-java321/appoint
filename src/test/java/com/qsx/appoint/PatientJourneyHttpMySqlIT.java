package com.qsx.appoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 通过真实监听端口和隔离 MySQL 验证患者完整 HTTP 旅程。 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "appoint.expiration.enabled=true",
                "appoint.appointment.payment-timeout=3s",
                "appoint.expiration.scan-delay=100ms"
        })
class PatientJourneyHttpMySqlIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("appoint")
            .withUsername("appoint")
            .withPassword("appoint-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @LocalServerPort private int port;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void patientCanRegisterBookPayCancelAndBookReleasedScheduleAgainOverHttp() throws Exception {
        String ownerEmail = "http-owner-" + UUID.randomUUID() + "@example.com";
        HttpResult registered = register(ownerEmail);
        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.json().at("/data/id").isTextual()).isTrue();
        assertThat(register(ownerEmail).status()).isEqualTo(409);

        HttpResult wrongPassword = send("POST", "/api/v1/auth/login", null,
                credentials(ownerEmail, "incorrect123"));
        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(wrongPassword.json().path("code").asInt()).isEqualTo(2003);

        String ownerToken = login(ownerEmail);
        String ownerPatientId = createPatient(ownerToken);
        HttpResult loginPayload = send("POST", "/api/v1/auth/login", null,
                credentials(ownerEmail, "password123"));
        assertThat(loginPayload.json().at("/data/expiresIn").isNumber()).isTrue();
        assertThat(send("GET", "/api/v1/users/me", "invalid-token", null).status()).isEqualTo(401);

        HttpResult hospitals = send("GET", "/api/v1/hospitals", ownerToken, null);
        assertThat(hospitals.status()).isEqualTo(200);
        assertThat(hospitals.json().at("/data/0/id").asText()).isEqualTo("1");
        assertThat(send("GET", "/api/v1/hospitals/1/departments", ownerToken, null).status()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/departments/1/doctors", ownerToken, null)
                .json().at("/data/0/doctorNo").asText()).isEqualTo("DOC000001");

        Long paidScheduleId = createSchedule(LocalDate.of(2099, 7, 1), LocalTime.of(8, 0));
        Long cancelledScheduleId = createSchedule(LocalDate.of(2099, 7, 1), LocalTime.of(9, 0));
        HttpResult schedules = send("GET", "/api/v1/doctors/1/schedules", ownerToken, null);
        assertThat(schedules.json().path("data").findValuesAsText("id"))
                .contains(paidScheduleId.toString(), cancelledScheduleId.toString());

        HttpResult invalidAppointment = send("POST", "/api/v1/appointments", ownerToken, "{}",
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(invalidAppointment.status()).isEqualTo(400);
        assertThat(invalidAppointment.json().path("code").asInt()).isEqualTo(1001);

        HttpResult paidAppointment = createAppointment(ownerToken, ownerPatientId, paidScheduleId);
        String paidAppointmentId = paidAppointment.json().at("/data/appointmentOrderId").asText();
        String paymentId = paidAppointment.json().at("/data/paymentOrderId").asText();
        assertThat(paidAppointment.json().at("/data/appointmentOrderId").isTextual()).isTrue();
        assertThat(paidAppointment.json().at("/data/paymentStatus").asText()).isEqualTo("UNPAID");
        HttpResult paidDetail = send("GET", "/api/v1/appointments/" + paidAppointmentId, ownerToken, null);
        assertThat(paidDetail.status()).isEqualTo(200);
        assertThat(paidDetail.json().at("/data/patient/id").asText()).isEqualTo(ownerPatientId);
        assertThat(paidDetail.json().at("/data/patient/name").asText()).isEqualTo("HTTP 就诊人");
        assertThat(paidDetail.json().at("/data/patient/documentNoMasked").asText())
                .isEqualTo("110105********0010");
        assertThat(paidDetail.json().at("/data/patient/contactEmail").asText())
                .isEqualTo("patient@example.com");
        assertThat(send("GET", "/api/v1/appointments/my", ownerToken, null).json().path("data")).hasSize(1);

        String otherEmail = "http-other-" + UUID.randomUUID() + "@example.com";
        register(otherEmail);
        String otherToken = login(otherEmail);
        String otherPatientId = createPatient(otherToken);
        HttpResult occupied = createAppointment(otherToken, otherPatientId, paidScheduleId);
        assertThat(occupied.status()).isEqualTo(409);
        assertThat(occupied.json().path("code").asInt()).isEqualTo(4002);
        assertThat(send("GET", "/api/v1/appointments/" + paidAppointmentId, otherToken, null).status())
                .isEqualTo(403);

        HttpResult paid = send("POST", "/api/v1/payments/" + paymentId + "/simulate-success", ownerToken, null);
        assertThat(paid.status()).isEqualTo(200);
        assertThat(paid.json().at("/data/appointmentStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(send("POST", "/api/v1/payments/" + paymentId + "/simulate-success", ownerToken, null).status())
                .isEqualTo(200);
        HttpResult confirmedCancel = send("POST", "/api/v1/appointments/" + paidAppointmentId + "/cancel",
                ownerToken, null);
        assertThat(confirmedCancel.status()).isEqualTo(409);
        assertThat(confirmedCancel.json().path("code").asInt()).isEqualTo(4003);

        HttpResult toCancel = createAppointment(ownerToken, ownerPatientId, cancelledScheduleId);
        String cancelledAppointmentId = toCancel.json().at("/data/appointmentOrderId").asText();
        HttpResult cancelled = send("POST", "/api/v1/appointments/" + cancelledAppointmentId + "/cancel",
                ownerToken, null);
        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.json().at("/data/status").asText()).isEqualTo("CANCELLED");
        assertThat(send("POST", "/api/v1/appointments/" + cancelledAppointmentId + "/cancel",
                ownerToken, null).status()).isEqualTo(200);

        HttpResult rebooked = createAppointment(otherToken, otherPatientId, cancelledScheduleId);
        assertThat(rebooked.status()).isEqualTo(201);
        assertThat(rebooked.json().at("/data/appointmentStatus").asText()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void shortPaymentDeadlineIsClosedByRealExpirationJobAndScheduleReturnsToCatalog() throws Exception {
        String email = "http-expiration-" + UUID.randomUUID() + "@example.com";
        register(email);
        String token = login(email);
        String patientId = createPatient(token);
        Long scheduleId = createSchedule(LocalDate.of(2099, 7, 2), LocalTime.of(10, 0));
        HttpResult created = createAppointment(token, patientId, scheduleId);
        assertThat(created.status()).isEqualTo(201);
        String appointmentId = created.json().at("/data/appointmentOrderId").asText();

        HttpResult detail = send("GET", "/api/v1/appointments/" + appointmentId, token, null);
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(8).toNanos();
        while (!"EXPIRED".equals(detail.json().at("/data/status").asText()) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            detail = send("GET", "/api/v1/appointments/" + appointmentId, token, null);
        }

        assertThat(detail.json().at("/data/status").asText()).isEqualTo("EXPIRED");
        assertThat(detail.json().at("/data/paymentStatus").asText()).isEqualTo("CLOSED");
        assertThat(detail.json().at("/data/paymentExpired").asBoolean()).isTrue();
        HttpResult schedules = send("GET", "/api/v1/doctors/1/schedules", token, null);
        assertThat(schedules.json().path("data").findValuesAsText("id")).contains(scheduleId.toString());
    }

    private HttpResult register(String email) throws Exception {
        return send("POST", "/api/v1/auth/register", null, credentials(email, "password123"));
    }

    private String login(String email) throws Exception {
        HttpResult response = send("POST", "/api/v1/auth/login", null, credentials(email, "password123"));
        assertThat(response.status()).isEqualTo(200);
        return response.json().at("/data/accessToken").asText();
    }

    private HttpResult createAppointment(String token, String patientId, Long scheduleId) throws Exception {
        return send("POST", "/api/v1/appointments", token,
                "{\"scheduleId\":\"" + scheduleId + "\",\"patientId\":\"" + patientId + "\"}",
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private String createPatient(String token) throws Exception {
        HttpResult response = send("POST", "/api/v1/patients", token,
                "{\"name\":\"HTTP 就诊人\",\"documentType\":\"RESIDENT_ID_CARD\"," +
                        "\"documentNo\":\"110105199001010010\",\"contactPhone\":\"13800138000\"," +
                        "\"contactEmail\":\"patient@example.com\"}");
        assertThat(response.status()).isEqualTo(201);
        return response.json().at("/data/id").asText();
    }

    private String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private Long createSchedule(LocalDate date, LocalTime time) {
        Schedule schedule = new Schedule();
        schedule.setDoctorId(1L);
        schedule.setAppointmentDate(date);
        schedule.setStartTime(time);
        schedule.setEndTime(time.plusMinutes(30));
        schedule.setStatus("AVAILABLE");
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }

    private HttpResult send(String method, String path, String token, String body) throws Exception {
        return send(method, path, token, body, Map.of());
    }

    private HttpResult send(String method, String path, String token, String body,
                            Map<String, String> extraHeaders) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        extraHeaders.forEach(builder::header);
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.headers().firstValue("content-type").orElse("").contains("application/json")
                ? objectMapper.readTree(response.body()) : objectMapper.nullNode();
        return new HttpResult(response.statusCode(), json, response.body());
    }

    private record HttpResult(int status, JsonNode json, String raw) { }
}

package com.qsx.appoint.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.appointment.config.ExpirationProperties;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.job.AppointmentExpirationJob;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentLifecycleService;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentLifecycleIntegrationTest {
    private static final AtomicInteger SCHEDULE_SEQUENCE = new AtomicInteger();
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private AppointmentOrderMapper appointmentOrderMapper;
    @Autowired private PaymentOrderMapper paymentOrderMapper;
    @Autowired private AppointmentLifecycleService lifecycleService;
    @Autowired private Clock appointmentClock;

    @Test
    void cancelIsIdempotentAndOldAppointmentCannotReleaseNewHold() throws Exception {
        AuthenticatedUser owner = registerAndLogin();
        Schedule schedule = createSchedule(LocalDate.of(2099, 3, 1), LocalTime.of(9, 0));
        CreatedAppointment first = createAppointment(owner, schedule.getId());

        String firstCancel = mockMvc.perform(post("/api/v1/appointments/{id}/cancel", first.appointmentId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.canPay").value(false))
                .andExpect(jsonPath("$.data.canCancel").value(false))
                .andExpect(jsonPath("$.data.appointmentDate").value(schedule.getAppointmentDate().toString()))
                .andExpect(jsonPath("$.data.startTime").value(schedule.getStartTime() + ":00"))
                .andExpect(jsonPath("$.data.endTime").value(schedule.getEndTime() + ":00"))
                .andExpect(jsonPath("$.data.doctor.hospital.address").value("示例市健康路 1 号"))
                .andReturn().getResponse().getContentAsString();
        String closedAt = objectMapper.readTree(firstCancel).at("/data/closedAt").asText();
        assertThat(scheduleMapper.selectById(schedule.getId()).getAvailableCapacity()).isEqualTo(5);

        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", first.appointmentId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closedAt").value(closedAt));
        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", first.paymentId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4003));

        AuthenticatedUser secondUser = registerAndLogin();
        CreatedAppointment second = createAppointment(secondUser, schedule.getId());
        assertThat(scheduleMapper.selectById(schedule.getId()).getHeldCapacity()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", first.appointmentId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        assertThat(scheduleMapper.selectById(schedule.getId()).getHeldCapacity()).isEqualTo(1);
        assertThat(appointmentOrderMapper.selectById(second.appointmentId()).getStatus()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void expiredPaymentIsRejectedAndScannerClosesOrder() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        Schedule schedule = createSchedule(LocalDate.of(2099, 3, 2), LocalTime.of(10, 0));
        CreatedAppointment created = createAppointment(user, schedule.getId());
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.appointmentId());
        appointment.setPaymentExpiresAt(LocalDateTime.now(appointmentClock).minusSeconds(1));
        appointmentOrderMapper.updateById(appointment);

        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", created.paymentId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4004));
        mockMvc.perform(get("/api/v1/appointments/{id}", created.appointmentId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.paymentExpired").value(true))
                .andExpect(jsonPath("$.data.canPay").value(false))
                .andExpect(jsonPath("$.data.canCancel").value(true));
        assertThat(scheduleMapper.selectById(schedule.getId()).getHeldCapacity()).isEqualTo(1);

        AppointmentExpirationJob job = new AppointmentExpirationJob(appointmentOrderMapper, lifecycleService,
                new ExpirationProperties(true, Duration.ofSeconds(30), 100), appointmentClock);
        job.scanExpiredAppointments();

        assertThat(appointmentOrderMapper.selectById(created.appointmentId()).getStatus()).isEqualTo("EXPIRED");
        assertThat(paymentOrderMapper.selectById(created.paymentId()).getStatus()).isEqualTo("CLOSED");
        assertThat(scheduleMapper.selectById(schedule.getId()).getHeldCapacity()).isZero();
        assertThat(scheduleMapper.selectById(schedule.getId()).getAvailableCapacity()).isEqualTo(5);
        assertThat(lifecycleService.expireIfDue(created.appointmentId())).isFalse();
    }

    @Test
    void cancelAfterDeadlineClosesAsExpired() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        Schedule schedule = createSchedule(LocalDate.of(2099, 3, 3), LocalTime.of(11, 0));
        CreatedAppointment created = createAppointment(user, schedule.getId());
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.appointmentId());
        appointment.setPaymentExpiresAt(LocalDateTime.now(appointmentClock).minusSeconds(1));
        appointmentOrderMapper.updateById(appointment);

        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", created.appointmentId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("CLOSED"));
    }

    @Test
    void confirmedAppointmentCanBeCancelledAndSimulatedRefundedBeforeDeadline() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment created = createAppointment(user,
                createSchedule(LocalDate.of(2099, 3, 4), LocalTime.of(12, 0)).getId());
        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", created.paymentId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", created.appointmentId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("模拟全额退款成功"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.data.visitNumber").value(1));
    }

    @Test
    void onlyOwnerCanCancelAppointment() throws Exception {
        AuthenticatedUser owner = registerAndLogin();
        AuthenticatedUser other = registerAndLogin();
        CreatedAppointment created = createAppointment(owner,
                createSchedule(LocalDate.of(2099, 3, 5), LocalTime.of(13, 0)).getId());

        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", created.appointmentId())
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", created.paymentId())
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2002));
        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", Long.MAX_VALUE)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void startedScheduleCannotBeListedOrBooked() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        Schedule schedule = createSchedule(LocalDate.now(appointmentClock).minusDays(1), LocalTime.of(8, 0));

        mockMvc.perform(get("/api/v1/doctors/1/schedules").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + schedule.getId() + "')]").isEmpty());
        mockMvc.perform(post("/api/v1/appointments").header("Authorization", bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":" + schedule.getId() + ",\"patientId\":" + user.patientId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4202));
    }

    @Test
    void paymentDeadlineUsesFifteenMinutes() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment normal = createAppointment(user,
                createSchedule(LocalDate.of(2099, 3, 6), LocalTime.of(14, 0)).getId());
        AppointmentOrder normalOrder = appointmentOrderMapper.selectById(normal.appointmentId());
        assertThat(normalOrder.getPaymentExpiresAt()).isEqualTo(normalOrder.getCreatedAt().plusMinutes(15));

    }

    private AuthenticatedUser registerAndLogin() throws Exception {
        String email = "lifecycle-" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).at("/data/accessToken").asText();
        String patientResponse = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试就诊人\",\"documentType\":\"RESIDENT_ID_CARD\"," +
                                "\"documentNo\":\"110105199001010010\",\"contactPhone\":\"13800138000\"," +
                                "\"contactEmail\":\"patient@example.com\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new AuthenticatedUser(token, objectMapper.readTree(patientResponse).at("/data/id").asLong());
    }

    private CreatedAppointment createAppointment(AuthenticatedUser user, Long scheduleId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":" + scheduleId + ",\"patientId\":" + user.patientId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.serverTime").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).get("data");
        return new CreatedAppointment(data.get("appointmentOrderId").asLong(), data.get("paymentOrderId").asLong());
    }

    private Schedule createSchedule(LocalDate date, LocalTime start) {
        Schedule schedule = new Schedule();
        schedule.setDoctorId(1L);
        boolean past = date.isBefore(LocalDate.now(appointmentClock));
        int sequence = SCHEDULE_SEQUENCE.getAndIncrement();
        boolean afternoon = !past && sequence % 2 == 1;
        schedule.setAppointmentDate(past ? date : LocalDate.now(appointmentClock).plusDays(2 + sequence / 2));
        schedule.setStartTime(afternoon ? LocalTime.of(14, 0) : LocalTime.of(8, 0));
        schedule.setEndTime(afternoon ? LocalTime.of(17, 0) : LocalTime.of(12, 0));
        schedule.setStatus("OPEN");
        schedule.setSessionType(afternoon ? "AFTERNOON" : "MORNING");
        schedule.setTotalCapacity(5);
        schedule.setAvailableCapacity(5);
        schedule.setHeldCapacity(0);
        schedule.setConfirmedCapacity(0);
        schedule.setNextVisitNumber(1);
        scheduleMapper.insert(schedule);
        return schedule;
    }

    private String bearer(AuthenticatedUser user) {
        return "Bearer " + user.token();
    }

    private record AuthenticatedUser(String token, Long patientId) { }
    private record CreatedAppointment(Long appointmentId, Long paymentId) { }
}

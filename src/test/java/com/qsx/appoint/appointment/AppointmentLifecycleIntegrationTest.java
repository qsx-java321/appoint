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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentLifecycleIntegrationTest {
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
                .andExpect(jsonPath("$.data.appointmentDate").value("2099-03-01"))
                .andExpect(jsonPath("$.data.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("09:30:00"))
                .andExpect(jsonPath("$.data.doctor.hospital.address").value("示例市健康路 1 号"))
                .andReturn().getResponse().getContentAsString();
        String closedAt = objectMapper.readTree(firstCancel).at("/data/closedAt").asText();
        assertThat(scheduleMapper.selectById(schedule.getId()).getStatus()).isEqualTo("AVAILABLE");

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
        assertThat(scheduleMapper.selectById(schedule.getId()).getStatus()).isEqualTo("HELD");

        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", first.appointmentId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        assertThat(scheduleMapper.selectById(schedule.getId()).getStatus()).isEqualTo("HELD");
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
        assertThat(scheduleMapper.selectById(schedule.getId()).getStatus()).isEqualTo("HELD");

        AppointmentExpirationJob job = new AppointmentExpirationJob(appointmentOrderMapper, lifecycleService,
                new ExpirationProperties(true, Duration.ofSeconds(30), 100), appointmentClock);
        job.scanExpiredAppointments();

        assertThat(appointmentOrderMapper.selectById(created.appointmentId()).getStatus()).isEqualTo("EXPIRED");
        assertThat(paymentOrderMapper.selectById(created.paymentId()).getStatus()).isEqualTo("CLOSED");
        assertThat(scheduleMapper.selectById(schedule.getId()).getStatus()).isEqualTo("AVAILABLE");
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
    void confirmedAppointmentCannotBeCancelled() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment created = createAppointment(user,
                createSchedule(LocalDate.of(2099, 3, 4), LocalTime.of(12, 0)).getId());
        mockMvc.perform(post("/api/v1/payments/{id}/simulate-success", created.paymentId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/appointments/{id}/cancel", created.appointmentId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4003));
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":" + schedule.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4005));
    }

    @Test
    void paymentDeadlineUsesFifteenMinutesOrAppointmentStartWhicheverComesFirst() throws Exception {
        AuthenticatedUser user = registerAndLogin();
        CreatedAppointment normal = createAppointment(user,
                createSchedule(LocalDate.of(2099, 3, 6), LocalTime.of(14, 0)).getId());
        AppointmentOrder normalOrder = appointmentOrderMapper.selectById(normal.appointmentId());
        assertThat(normalOrder.getPaymentExpiresAt()).isEqualTo(normalOrder.getCreatedAt().plusMinutes(15));

        LocalDateTime nearStart = LocalDateTime.now(appointmentClock).plusMinutes(5).withNano(0);
        CreatedAppointment near = createAppointment(user,
                createSchedule(nearStart.toLocalDate(), nearStart.toLocalTime()).getId());
        assertThat(appointmentOrderMapper.selectById(near.appointmentId()).getPaymentExpiresAt())
                .isEqualTo(nearStart);
    }

    private AuthenticatedUser registerAndLogin() throws Exception {
        String email = "lifecycle-" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new AuthenticatedUser(objectMapper.readTree(response).at("/data/accessToken").asText());
    }

    private CreatedAppointment createAppointment(AuthenticatedUser user, Long scheduleId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":" + scheduleId + "}"))
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
        schedule.setAppointmentDate(date);
        schedule.setStartTime(start);
        schedule.setEndTime(start.plusMinutes(30));
        schedule.setStatus("AVAILABLE");
        scheduleMapper.insert(schedule);
        return schedule;
    }

    private String bearer(AuthenticatedUser user) {
        return "Bearer " + user.token();
    }

    private record AuthenticatedUser(String token) { }
    private record CreatedAppointment(Long appointmentId, Long paymentId) { }
}

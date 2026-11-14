package com.qsx.appoint.catalog;

import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.catalog.model.AppointmentSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import com.qsx.appoint.support.MutableAppointmentClock;
import com.qsx.appoint.support.AppointmentClockTestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spring/H2验证目录的三天场次投影和只读查询；每例重置上海时钟并回滚设置变更。 */
@RegressionTest(properties =
        "spring.datasource.url=jdbc:h2:mem:session-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Transactional
@Import(AppointmentClockTestConfiguration.class)
class SessionCatalogHttpTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MutableAppointmentClock clock;

    @BeforeEach
    void resetClock() { clock.set("2026-11-05T10:05:00"); }

    @Test
    void doctorCatalogReturnsTwelveSessionsWithOneTimeSnapshotWithoutWritingSchedules() throws Exception {
        long before = scheduleMapper.selectCount(null);

        mockMvc.perform(get("/api/v1/doctors/1/schedules")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].appointmentDate").value("2026-11-05"))
                .andExpect(jsonPath("$.data[0].sessionType").value("MORNING_FIRST"))
                .andExpect(jsonPath("$.data[0].startTime").value("08:00:00"))
                .andExpect(jsonPath("$.data[0].endTime").value("10:00:00"))
                .andExpect(jsonPath("$.data[0].totalCapacity").value(20))
                .andExpect(jsonPath("$.data[0].availableCapacity").value(20))
                .andExpect(jsonPath("$.data[0].registrationFee").value("10.00"))
                .andExpect(jsonPath("$.data[0].feeCurrency").value("CNY"))
                .andExpect(jsonPath("$.data[0].bookable").value(false))
                .andExpect(jsonPath("$.data[0].availability").value("STARTED"))
                .andExpect(jsonPath("$.data[1].bookable").value(true))
                .andExpect(jsonPath("$.data[1].bookingClosesAt").value("2026-11-05T10:15:00"))
                .andExpect(jsonPath("$.data[11].appointmentDate").value("2026-11-07"))
                .andExpect(jsonPath("$.data[11].sessionType").value("AFTERNOON_SECOND"));

        var rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            assertThat(rows.get(i).path("serverTime").asText()).isEqualTo("2026-11-05T10:05:00");
            assertThat(rows.get(i).path("appointmentDate").asText())
                    .isEqualTo(LocalDate.of(2026, 11, 5).plusDays(i / 4).toString());
            assertThat(rows.get(i).path("sessionType").asText()).isEqualTo(AppointmentSession.values()[i % 4].name());
        }

        assertThat(scheduleMapper.selectCount(null)).isEqualTo(before);
    }

    @Test
    void timeStateTakesPrecedenceOverFullInventory() throws Exception {
        var date = LocalDate.of(2026, 11, 5);
        var session = AppointmentSession.MORNING_SECOND;
        scheduleMapper.insertGenerated(1L, date, session.startTime(), session.endTime(), session.name(),
                1, new java.math.BigDecimal("10.00"), "CNY");
        var schedule = scheduleMapper.selectByDoctorDateSession(1L, date, session.name());
        assertThat(scheduleMapper.holdOne(schedule.getId())).isEqualTo(1);

        assertThat(rows().get(1).path("availability").asText()).isEqualTo("FULL");
        assertThat(rows().get(2).path("bookable").asBoolean()).isTrue();
        clock.set("2026-11-05T10:15:00");
        assertThat(rows().get(1).path("availability").asText()).isEqualTo("CUTOFF_REACHED");
        assertThat(rows().get(1).path("bookable").asBoolean()).isFalse();
        clock.set("2026-11-05T10:30:00");
        assertThat(rows().get(1).path("availability").asText()).isEqualTo("STARTED");
        var after = scheduleMapper.selectById(schedule.getId());
        assertThat(after.getAvailableCapacity()).isZero();
        assertThat(after.getHeldCapacity()).isEqualTo(1);
    }

    @Test
    void midnightRollsAllThreeDatesWithoutPersistingSessions() throws Exception {
        long before = scheduleMapper.selectCount(null);
        clock.set("2026-11-05T23:59:59");
        assertThat(rows().get(0).path("appointmentDate").asText()).isEqualTo("2026-11-05");
        clock.set("2026-11-06T00:00:00");
        var rows = rows();
        assertThat(rows.get(0).path("appointmentDate").asText()).isEqualTo("2026-11-06");
        assertThat(rows.get(11).path("appointmentDate").asText()).isEqualTo("2026-11-08");
        for (var row : rows) assertThat(row.path("serverTime").asText()).isEqualTo("2026-11-06T00:00:00");
        assertThat(scheduleMapper.selectCount(null)).isEqualTo(before);
    }

    private com.fasterxml.jackson.databind.JsonNode rows() throws Exception {
        var response = mockMvc.perform(get("/api/v1/doctors/1/schedules").with(user("user").roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    @Test
    void ungeneratedSessionsReflectCurrentSettings() throws Exception {
        mockMvc.perform(put("/api/v1/admin/booking-settings")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacityPerSession\":30,\"registrationFee\":12.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/doctors/1/schedules")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].totalCapacity").value(30))
                .andExpect(jsonPath("$.data[0].availableCapacity").value(30))
                .andExpect(jsonPath("$.data[0].registrationFee").value("12.00"));
    }

}

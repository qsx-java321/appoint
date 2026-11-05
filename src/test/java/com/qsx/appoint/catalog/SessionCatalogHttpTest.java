package com.qsx.appoint.catalog;

import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:session-catalog;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Transactional
@Import(SessionCatalogHttpTest.FixedClockConfig.class)
class SessionCatalogHttpTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ScheduleMapper scheduleMapper;

    @Test
    void doctorCatalogReturnsTwentyEightFutureSessionsWithoutWritingSchedules() throws Exception {
        long before = scheduleMapper.selectCount(null);

        mockMvc.perform(get("/api/v1/doctors/1/schedules")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(28))
                .andExpect(jsonPath("$.data[0].appointmentDate").value("2026-11-06"))
                .andExpect(jsonPath("$.data[0].sessionType").value("MORNING_FIRST"))
                .andExpect(jsonPath("$.data[0].startTime").value("08:00:00"))
                .andExpect(jsonPath("$.data[0].endTime").value("10:00:00"))
                .andExpect(jsonPath("$.data[0].totalCapacity").value(20))
                .andExpect(jsonPath("$.data[0].availableCapacity").value(20))
                .andExpect(jsonPath("$.data[0].registrationFee").value("10.00"))
                .andExpect(jsonPath("$.data[0].feeCurrency").value("CNY"))
                .andExpect(jsonPath("$.data[0].bookable").value(true))
                .andExpect(jsonPath("$.data[27].appointmentDate").value("2026-11-12"))
                .andExpect(jsonPath("$.data[27].sessionType").value("AFTERNOON_SECOND"));

        assertThat(scheduleMapper.selectCount(null)).isEqualTo(before);
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

    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedAppointmentClock() {
            return Clock.fixed(Instant.parse("2026-11-05T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}

package com.qsx.appoint.appointment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.entity.AppointmentActiveClaim;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentActiveClaimMapper;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.catalog.dto.AdjustScheduleInventoryRequest;
import com.qsx.appoint.catalog.dto.CreateScheduleRequest;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.entity.ScheduleInventoryAdjustment;
import com.qsx.appoint.catalog.mapper.ScheduleInventoryAdjustmentMapper;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.catalog.service.ScheduleManagementService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.service.PaymentService;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:schedule-inventory;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.expiration.enabled=false"
})
@AutoConfigureMockMvc
@Import(ScheduleInventoryIntegrationTest.ClockConfiguration.class)
class ScheduleInventoryIntegrationTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant RELEASE_INSTANT = ZonedDateTime.of(
            2026, 11, 1, 8, 0, 0, 0, SHANGHAI).toInstant();

    @Autowired private MutableClock clock;
    @Autowired private ScheduleManagementService scheduleManagementService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private PaymentService paymentService;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private AppointmentOrderMapper appointmentOrderMapper;
    @Autowired private PaymentOrderMapper paymentOrderMapper;
    @Autowired private AppointmentActiveClaimMapper activeClaimMapper;
    @Autowired private ScheduleInventoryAdjustmentMapper adjustmentMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void resetClock() {
        clock.setInstant(RELEASE_INSTANT);
    }

    @Test
    void releaseBoundaryIsInclusiveAndBookingCutoffBoundaryIsExclusive() {
        Long operator = createUser("operator");
        Schedule schedule = createSchedule(operator, 1L, LocalDate.of(2026, 11, 8), "MORNING", 2);
        Long user = createUser("boundary-user");
        Long firstPatient = createPatient(user, "边界甲", "110105199001010010");

        createAppointment(user, firstPatient, schedule.getId());
        clock.setInstant(ZonedDateTime.of(2026, 11, 8, 7, 30, 0, 0, SHANGHAI).toInstant());
        Long secondPatient = createPatient(user, "边界乙", "110105199002020026");
        assertThatThrownBy(() -> createAppointment(user, secondPatient, schedule.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SCHEDULE_CUTOFF_REACHED));
    }

    @Test
    void crossAccountSameDocumentIsBlockedUntilFirstClaimIsReleased() {
        Long operator = createUser("operator-duplicate");
        Schedule schedule = createSchedule(operator, 1L, LocalDate.of(2026, 11, 8), "AFTERNOON", 2);
        Long firstUser = createUser("duplicate-first");
        Long secondUser = createUser("duplicate-second");
        Long firstPatient = createPatient(firstUser, "孩子甲", "110105199001010010");
        Long secondPatient = createPatient(secondUser, "孩子乙", "110105199001010010");
        CreateAppointmentResponse first = createAppointment(firstUser, firstPatient, schedule.getId());
        Patient edited = patientMapper.selectById(firstPatient);
        edited.setDocumentNo("110105199002020026");
        patientMapper.updateById(edited);

        assertThatThrownBy(() -> createAppointment(secondUser, secondPatient, schedule.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ACTIVE_APPOINTMENT));
        Schedule held = scheduleMapper.selectById(schedule.getId());
        assertInventory(held, 2, 1, 1, 0);

        appointmentService.cancel(firstUser, first.getAppointmentOrderId());
        createAppointment(secondUser, secondPatient, schedule.getId());
        assertInventory(scheduleMapper.selectById(schedule.getId()), 2, 1, 1, 0);
    }

    @Test
    void visitNumbersAreMonotonicAndCancelledNumberIsNotReused() {
        Long operator = createUser("operator-sequence");
        Schedule schedule = createSchedule(operator, 2L, LocalDate.of(2026, 11, 8), "MORNING", 2);
        Long user = createUser("sequence-user");
        CreateAppointmentResponse first = createAppointment(user,
                createPatient(user, "序号甲", "110105199001010010"), schedule.getId());
        CreateAppointmentResponse second = createAppointment(user,
                createPatient(user, "序号乙", "110105199002020026"), schedule.getId());
        paymentService.simulateSuccess(user, first.getPaymentOrderId());
        paymentService.simulateSuccess(user, second.getPaymentOrderId());
        appointmentService.cancel(user, first.getAppointmentOrderId());
        CreateAppointmentResponse third = createAppointment(user,
                createPatient(user, "序号丙", "110105199003030031"), schedule.getId());
        paymentService.simulateSuccess(user, third.getPaymentOrderId());

        assertThat(appointmentOrderMapper.selectById(first.getAppointmentOrderId()).getVisitNumber()).isEqualTo(1);
        assertThat(appointmentOrderMapper.selectById(second.getAppointmentOrderId()).getVisitNumber()).isEqualTo(2);
        assertThat(appointmentOrderMapper.selectById(third.getAppointmentOrderId()).getVisitNumber()).isEqualTo(3);
        assertThat(paymentOrderMapper.selectById(first.getPaymentOrderId()).getStatus()).isEqualTo("REFUNDED");
        Schedule finalInventory = scheduleMapper.selectById(schedule.getId());
        assertInventory(finalInventory, 2, 0, 0, 2);
        assertThat(finalInventory.getNextVisitNumber()).isEqualTo(4);
    }

    @Test
    void confirmedCancellationIsRejectedAtExactTwentyFourHourBoundary() {
        Long operator = createUser("operator-refund-boundary");
        Schedule schedule = createSchedule(operator, 2L, LocalDate.of(2026, 11, 2), "MORNING", 1);
        Long user = createUser("refund-boundary-user");
        CreateAppointmentResponse created = createAppointment(user,
                createPatient(user, "退款边界", "110105199001010010"), schedule.getId());
        paymentService.simulateSuccess(user, created.getPaymentOrderId());

        assertThatThrownBy(() -> appointmentService.cancel(user, created.getAppointmentOrderId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CANCELLATION_DEADLINE_REACHED));
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getStatus())
                .isEqualTo("CONFIRMED");
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getStatus()).isEqualTo("PAID");
        assertInventory(scheduleMapper.selectById(schedule.getId()), 1, 0, 0, 1);
    }

    @Test
    void closingScheduleExpiresOverdueHoldAndHospitalCancelsRemainingHold() {
        Long operator = createUser("operator-close");
        Schedule schedule = createSchedule(operator, 2L, LocalDate.of(2026, 11, 8), "AFTERNOON", 2);
        Long firstUser = createUser("close-first");
        Long secondUser = createUser("close-second");
        CreateAppointmentResponse overdue = createAppointment(firstUser,
                createPatient(firstUser, "停诊甲", "110105199001010010"), schedule.getId());
        CreateAppointmentResponse current = createAppointment(secondUser,
                createPatient(secondUser, "停诊乙", "110105199002020026"), schedule.getId());
        AppointmentOrder overdueOrder = appointmentOrderMapper.selectById(overdue.getAppointmentOrderId());
        overdueOrder.setPaymentExpiresAt(LocalDateTime.ofInstant(RELEASE_INSTANT.minusSeconds(1), SHANGHAI));
        appointmentOrderMapper.updateById(overdueOrder);

        scheduleManagementService.close(operator, schedule.getId());

        assertThat(appointmentOrderMapper.selectById(overdue.getAppointmentOrderId()).getStatus()).isEqualTo("EXPIRED");
        assertThat(appointmentOrderMapper.selectById(current.getAppointmentOrderId()).getStatus())
                .isEqualTo("CANCELLED_BY_HOSPITAL");
        assertThat(paymentOrderMapper.selectById(overdue.getPaymentOrderId()).getStatus()).isEqualTo("CLOSED");
        assertThat(paymentOrderMapper.selectById(current.getPaymentOrderId()).getStatus()).isEqualTo("CLOSED");
        Schedule closed = scheduleMapper.selectById(schedule.getId());
        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertInventory(closed, 2, 2, 0, 0);
        assertThat(activeClaimMapper.selectCount(new LambdaQueryWrapper<AppointmentActiveClaim>()
                .eq(AppointmentActiveClaim::getScheduleId, schedule.getId()))).isZero();
    }

    @Test
    void inventoryAdjustmentIsAuditedAndUserCannotCallAdminEndpoint() throws Exception {
        Long operator = createUser("operator-adjust");
        Schedule schedule = createSchedule(operator, 1L, LocalDate.of(2026, 11, 9), "MORNING", 3);
        AdjustScheduleInventoryRequest adjustment = new AdjustScheduleInventoryRequest();
        adjustment.setDelta(2);
        adjustment.setReason("临时加号");
        scheduleManagementService.adjustInventory(operator, schedule.getId(), adjustment);
        Schedule updated = scheduleMapper.selectById(schedule.getId());
        assertInventory(updated, 5, 5, 0, 0);
        ScheduleInventoryAdjustment audit = adjustmentMapper.selectOne(
                new LambdaQueryWrapper<ScheduleInventoryAdjustment>()
                        .eq(ScheduleInventoryAdjustment::getScheduleId, schedule.getId()));
        assertThat(audit.getOperatorUserId()).isEqualTo(operator);
        assertThat(audit.getReason()).isEqualTo("临时加号");

        mockMvc.perform(post("/api/v1/admin/schedules/{id}/close", schedule.getId())
                        .with(user("ordinary").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private Schedule createSchedule(Long operator, Long doctorId, LocalDate date, String session, int capacity) {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setDoctorId(doctorId);
        request.setAppointmentDate(date);
        request.setSessionType(session);
        request.setTotalCapacity(capacity);
        Long id = scheduleManagementService.create(operator, request).getId();
        return scheduleMapper.selectById(id);
    }

    private CreateAppointmentResponse createAppointment(Long userId, Long patientId, Long scheduleId) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setScheduleId(scheduleId);
        request.setPatientId(patientId);
        AppointmentCreationResult result = appointmentService.create(userId, UUID.randomUUID().toString(), request);
        return (CreateAppointmentResponse) result;
    }

    private Long createUser(String prefix) {
        User user = new User();
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("unused");
        userMapper.insert(user);
        return user.getId();
    }

    private Long createPatient(Long userId, String name, String documentNo) {
        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName(name);
        patient.setDocumentType("RESIDENT_ID_CARD");
        patient.setDocumentNo(documentNo);
        patient.setContactPhone("13800138000");
        patient.setContactEmail("patient@example.com");
        patient.setEnabled(true);
        patientMapper.insert(patient);
        return patient.getId();
    }

    private void assertInventory(Schedule schedule, int total, int available, int held, int confirmed) {
        assertThat(schedule.getTotalCapacity()).isEqualTo(total);
        assertThat(schedule.getAvailableCapacity()).isEqualTo(available);
        assertThat(schedule.getHeldCapacity()).isEqualTo(held);
        assertThat(schedule.getConfirmedCapacity()).isEqualTo(confirmed);
        assertThat(available + held + confirmed).isEqualTo(total);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(RELEASE_INSTANT, SHANGHAI);
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}

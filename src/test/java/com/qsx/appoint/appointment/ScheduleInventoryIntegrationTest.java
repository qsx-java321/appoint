package com.qsx.appoint.appointment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.AppointmentResponse;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.entity.AppointmentActiveClaim;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentActiveClaimMapper;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.catalog.dto.CreateScheduleRequest;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.catalog.service.ScheduleManagementService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
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

import java.time.*;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired private BookingCoordinator bookingCoordinator;
    @Autowired private BookingLifecycleCoordinator lifecycleCoordinator;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private AppointmentOrderMapper appointmentOrderMapper;
    @Autowired private PaymentOrderMapper paymentOrderMapper;
    @Autowired private AppointmentActiveClaimMapper activeClaimMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PatientMapper patientMapper;

    @BeforeEach
    void resetClock() {
        clock.setInstant(RELEASE_INSTANT);
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

        lifecycleCoordinator.cancelOwned(firstUser, first.getAppointmentOrderId());
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
        lifecycleCoordinator.simulatePaymentSuccess(user, first.getPaymentOrderId());
        lifecycleCoordinator.simulatePaymentSuccess(user, second.getPaymentOrderId());
        lifecycleCoordinator.cancelOwned(user, first.getAppointmentOrderId());
        CreateAppointmentResponse third = createAppointment(user,
                createPatient(user, "序号丙", "110105199003030031"), schedule.getId());
        lifecycleCoordinator.simulatePaymentSuccess(user, third.getPaymentOrderId());

        assertThat(appointmentOrderMapper.selectById(first.getAppointmentOrderId()).getVisitNumber()).isEqualTo(1);
        assertThat(appointmentOrderMapper.selectById(second.getAppointmentOrderId()).getVisitNumber()).isEqualTo(2);
        assertThat(appointmentOrderMapper.selectById(third.getAppointmentOrderId()).getVisitNumber()).isEqualTo(3);
        assertThat(paymentOrderMapper.selectById(first.getPaymentOrderId()).getStatus()).isEqualTo("REFUND_PENDING");
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
        lifecycleCoordinator.simulatePaymentSuccess(user, created.getPaymentOrderId());

        assertThatThrownBy(() -> lifecycleCoordinator.cancelOwned(user, created.getAppointmentOrderId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CANCELLATION_DEADLINE_REACHED));
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getStatus())
                .isEqualTo("CONFIRMED");
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getStatus()).isEqualTo("PAID");
        assertInventory(scheduleMapper.selectById(schedule.getId()), 1, 0, 0, 1);
    }

    @Test
    void paymentIsRejectedAtTheExactPaymentDeadlineWithoutChangingTheHold() {
        clock.setInstant(RELEASE_INSTANT.plus(Duration.ofDays(1)));
        Long operator = createUser("operator-payment-deadline");
        Schedule schedule = createSchedule(operator, 2L, LocalDate.of(2026, 11, 9), "MORNING", 1);
        Long user = createUser("payment-deadline-user");
        CreateAppointmentResponse created = createAppointment(user,
                createPatient(user, "支付边界", "110105199001010010"), schedule.getId());
        clock.setInstant(created.getPaymentExpiresAt().atZone(SHANGHAI).toInstant());

        assertThatThrownBy(() -> lifecycleCoordinator.simulatePaymentSuccess(user, created.getPaymentOrderId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_EXPIRED));

        AppointmentResponse detail = appointmentService.getOwned(user, created.getAppointmentOrderId());
        assertThat(detail.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(detail.getPaymentStatus()).isEqualTo("UNPAID");
        assertThat(detail.isPaymentExpired()).isTrue();
        assertThat(detail.isCanPay()).isFalse();
        assertInventory(scheduleMapper.selectById(schedule.getId()), 1, 0, 1, 0);
    }

    @Test
    void confirmedCancellationCanBeRepeatedWithoutChangingItsReceipt() {
        clock.setInstant(RELEASE_INSTANT.plus(Duration.ofDays(1)));
        Long operator = createUser("operator-refund-idempotency");
        Schedule schedule = createSchedule(operator, 1L, LocalDate.of(2026, 11, 9), "AFTERNOON", 1);
        Long user = createUser("refund-idempotency-user");
        CreateAppointmentResponse created = createAppointment(user,
                createPatient(user, "重复退款", "110105199001010010"), schedule.getId());
        lifecycleCoordinator.simulatePaymentSuccess(user, created.getPaymentOrderId());

        lifecycleCoordinator.cancelOwned(user, created.getAppointmentOrderId());
        AppointmentResponse first = appointmentService.getOwned(user, created.getAppointmentOrderId());
        lifecycleCoordinator.cancelOwned(user, created.getAppointmentOrderId());
        AppointmentResponse repeated = appointmentService.getOwned(user, created.getAppointmentOrderId());

        assertThat(first.getStatus()).isEqualTo("CANCELLED");
        assertThat(first.getPaymentStatus()).isEqualTo("REFUND_PENDING");
        assertThat(repeated.getStatus()).isEqualTo(first.getStatus());
        assertThat(repeated.getPaymentStatus()).isEqualTo(first.getPaymentStatus());
        assertThat(repeated.getClosedAt()).isEqualTo(first.getClosedAt());
        assertThat(repeated.getVisitNumber()).isEqualTo(first.getVisitNumber());
        assertInventory(scheduleMapper.selectById(schedule.getId()), 1, 1, 0, 0);
    }

    @Test
    void appointmentCreationCopiesAnImmutableFeeSnapshotToBothOrders() {
        Long operator = createUser("operator-snapshot");
        Schedule schedule = createSchedule(operator, 1L, LocalDate.of(2026, 11, 11), "MORNING", 1);
        clock.setInstant(ZonedDateTime.of(2026, 11, 4, 8, 0, 0, 0, SHANGHAI).toInstant());
        Long user = createUser("snapshot-user");
        CreateAppointmentResponse created = createAppointment(user,
                createPatient(user, "金额快照", "110105199001010010"), schedule.getId());

        assertThat(created.getAmount()).isEqualByComparingTo("50.00");
        assertThat(created.getCurrency()).isEqualTo("CNY");
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        PaymentOrder payment = paymentOrderMapper.selectById(created.getPaymentOrderId());
        assertThat(appointment.getFeeAmount()).isEqualByComparingTo("50.00");
        assertThat(appointment.getFeeCurrency()).isEqualTo("CNY");
        assertThat(payment.getAmount()).isEqualByComparingTo("50.00");
        assertThat(payment.getCurrency()).isEqualTo("CNY");
        assertThat(payment.getUserId()).isEqualTo(appointment.getUserId());
        assertThat(payment.getExpiresAt()).isEqualTo(created.getPaymentExpiresAt());
        assertThat(payment.getLegacy()).isFalse();

        schedule.setRegistrationFee(new BigDecimal("80.00"));
        scheduleMapper.updateById(schedule);
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getFeeAmount())
                .isEqualByComparingTo("50.00");
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getAmount())
                .isEqualByComparingTo("50.00");
    }

    private Schedule createSchedule(Long operator, Long doctorId, LocalDate date, String session, int capacity) {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setDoctorId(doctorId);
        request.setAppointmentDate(date);
        request.setSessionType("MORNING".equals(session) ? "MORNING_FIRST" :
                "AFTERNOON".equals(session) ? "AFTERNOON_FIRST" : session);
        request.setTotalCapacity(capacity);
        request.setRegistrationFee(new BigDecimal("50.00"));
        request.setFeeCurrency("CNY");
        Long id = scheduleManagementService.create(operator, request).getId();
        return scheduleMapper.selectById(id);
    }

    private CreateAppointmentResponse createAppointment(Long userId, Long patientId, Long scheduleId) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        request.setDoctorId(schedule.getDoctorId());
        request.setAppointmentDate(schedule.getAppointmentDate());
        request.setSessionType(schedule.getSessionType());
        request.setPatientId(patientId);
        AppointmentCreationResult result = bookingCoordinator.create(userId, UUID.randomUUID().toString(), request);
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

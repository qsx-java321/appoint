package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.AppointmentResponse;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.context.annotation.Import;

import java.time.*;
import java.math.BigDecimal;
import java.util.UUID;
import com.qsx.appoint.support.CommittedFixtureScope;
import com.qsx.appoint.support.MutableAppointmentClock;
import com.qsx.appoint.support.AppointmentClockTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spring/H2验证库存守恒、有效占用、序号和费用快照；可变钟每例重置，提交数据按例清理。 */
@RegressionTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:schedule-inventory;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "appoint.expiration.enabled=false"
})
@AutoConfigureMockMvc
@Import({AppointmentClockTestConfiguration.class, com.qsx.appoint.support.FormalPaymentTestSupport.class})
@org.springframework.test.context.ActiveProfiles("regression")
class ScheduleInventoryIntegrationTest {
    @Autowired private JdbcTemplate fixtureJdbc;
    private CommittedFixtureScope fixtureScope;

    @BeforeEach
    void trackCommittedFixture() { fixtureScope = new CommittedFixtureScope(fixtureJdbc); }

    @AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Autowired private com.qsx.appoint.support.FormalPaymentTestSupport formalPayments;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant RELEASE_INSTANT = ZonedDateTime.of(
            2026, 11, 1, 8, 0, 0, 0, SHANGHAI).toInstant();

    @Autowired private MutableAppointmentClock clock;
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
        Schedule schedule = createSchedule(operator, 1L, LocalDate.of(2026, 11, 3), "AFTERNOON", 2);
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
    void visitNumbersFollowConfirmationOrder() {
        Long operator = createUser("operator-sequence");
        Schedule schedule = createSchedule(operator, 2L, LocalDate.of(2026, 11, 3), "MORNING", 3);
        Long user = createUser("sequence-user");
        CreateAppointmentResponse first = createAppointment(user,
                createPatient(user, "序号甲", "110105199001010010"), schedule.getId());
        CreateAppointmentResponse second = createAppointment(user,
                createPatient(user, "序号乙", "110105199002020026"), schedule.getId());
        formalPayments.pay(user, first.getAppointmentOrderId());
        formalPayments.pay(user, second.getAppointmentOrderId());
        CreateAppointmentResponse third = createAppointment(user,
                createPatient(user, "序号丙", "110105199003030031"), schedule.getId());
        formalPayments.pay(user, third.getAppointmentOrderId());

        assertThat(appointmentOrderMapper.selectById(first.getAppointmentOrderId()).getVisitNumber()).isEqualTo(1);
        assertThat(appointmentOrderMapper.selectById(second.getAppointmentOrderId()).getVisitNumber()).isEqualTo(2);
        assertThat(appointmentOrderMapper.selectById(third.getAppointmentOrderId()).getVisitNumber()).isEqualTo(3);
        assertThat(paymentOrderMapper.selectById(first.getPaymentOrderId()).getStatus()).isEqualTo("PAID");
        Schedule finalInventory = scheduleMapper.selectById(schedule.getId());
        assertInventory(finalInventory, 3, 0, 0, 3);
        assertThat(finalInventory.getNextVisitNumber()).isEqualTo(4);
    }

    @Test
    void confirmedAppointmentCannotBeCancelled() {
        Long operator = createUser("operator-confirmed-cancel");
        Schedule schedule = createSchedule(operator, 2L, LocalDate.of(2026, 11, 2), "MORNING", 1);
        Long user = createUser("confirmed-cancel-user");
        CreateAppointmentResponse created = createAppointment(user,
                createPatient(user, "已确认取消", "110105199001010010"), schedule.getId());
        formalPayments.pay(user, created.getAppointmentOrderId());

        assertThatThrownBy(() -> lifecycleCoordinator.cancelOwned(user, created.getAppointmentOrderId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ORDER_STATE_ERROR));
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getStatus())
                .isEqualTo("CONFIRMED");
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getStatus()).isEqualTo("PAID");
        assertInventory(scheduleMapper.selectById(schedule.getId()), 1, 0, 0, 1);
    }

    @Test
    void paymentIsRejectedAfterThePaymentDeadlineWithoutChangingTheHold() {
        clock.setInstant(RELEASE_INSTANT.plus(Duration.ofDays(1)));
        Long operator = createUser("operator-payment-deadline");
        Schedule schedule = createSchedule(operator, 2L, LocalDate.of(2026, 11, 4), "MORNING", 1);
        Long user = createUser("payment-deadline-user");
        CreateAppointmentResponse created = createAppointment(user,
                createPatient(user, "支付边界", "110105199001010010"), schedule.getId());
        clock.setInstant(created.getPaymentExpiresAt().plusSeconds(1).atZone(SHANGHAI).toInstant());

        assertThatThrownBy(() -> formalPayments.pay(user, created.getAppointmentOrderId()))
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
    void appointmentCreationCopiesAnImmutableFeeSnapshotToBothOrders() {
        Long operator = createUser("operator-snapshot");
        Schedule schedule = createSchedule(operator, 1L, LocalDate.of(2026, 11, 6), "MORNING", 1);
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
        AppointmentCreationResult result = bookingCoordinator.create(userId, bookingCoordinator.issueToken(userId).token(), request);
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

}

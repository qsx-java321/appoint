package com.qsx.appoint.appointment;

import com.qsx.appoint.appointment.dto.AppointmentCreationResult;
import com.qsx.appoint.appointment.dto.CreateAppointmentRequest;
import com.qsx.appoint.appointment.dto.CreateAppointmentResponse;
import com.qsx.appoint.appointment.dto.ReplayedAppointmentResponse;
import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.catalog.entity.Schedule;
import com.qsx.appoint.catalog.mapper.DoctorMapper;
import com.qsx.appoint.support.BookingTestDoctors;
import com.qsx.appoint.catalog.mapper.ScheduleMapper;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.payment.entity.PaymentOrder;
import com.qsx.appoint.payment.application.PaymentAttemptService;
import com.qsx.appoint.payment.application.PaymentNotificationIngress;
import com.qsx.appoint.payment.adapter.mock.MockPaymentGateway;
import com.qsx.appoint.payment.domain.PaymentChannel;
import com.qsx.appoint.payment.dto.PaymentAttemptResponse;
import com.qsx.appoint.payment.mapper.PaymentOrderMapper;
import com.qsx.appoint.payment.mapper.PaymentRefundMapper;
import com.qsx.appoint.patient.entity.Patient;
import com.qsx.appoint.patient.mapper.PatientMapper;
import com.qsx.appoint.patient.dto.CreatePatientRequest;
import com.qsx.appoint.patient.dto.UpdatePatientRequest;
import com.qsx.appoint.patient.dto.PatientResponse;
import com.qsx.appoint.patient.service.PatientService;
import com.qsx.appoint.user.entity.User;
import com.qsx.appoint.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.qsx.appoint.support.RegressionTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import com.qsx.appoint.support.CommittedFixtureScope;
import com.qsx.appoint.support.TestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 类独占MySQL验证防超卖、并发幂等及回调/取消/过期竞争；保留真实屏障、事务和库存断言。 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
@RegressionTest(properties = "appoint.expiration.enabled=false")
@ActiveProfiles("regression")
@org.springframework.context.annotation.Import(com.qsx.appoint.support.FormalPaymentTestSupport.class)
class AppointmentLifecycleMySqlIT {
    private CommittedFixtureScope fixtureScope;

    @BeforeEach
    void trackCommittedFixture() { fixtureScope = new CommittedFixtureScope(jdbcTemplate); }

    @AfterEach
    void removeCommittedFixture() { fixtureScope.close(); }

    @Autowired private com.qsx.appoint.support.FormalPaymentTestSupport formalPayments;
    @Autowired private JdbcTemplate jdbcTemplate;
    private static final AtomicInteger SCHEDULE_SEQUENCE = new AtomicInteger();
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-lock-wait-timeout=10").withDatabaseName("appoint")
            .withUsername("appoint")
            .withPassword("appoint-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired private AppointmentService appointmentService;
    @Autowired private BookingCoordinator bookingCoordinator;
    @Autowired private BookingLifecycleCoordinator lifecycleService;
    @Autowired private AppointmentOrderMapper appointmentOrderMapper;
    @Autowired private PaymentOrderMapper paymentOrderMapper;
    @Autowired private PaymentRefundMapper paymentRefundMapper;
    @Autowired private PaymentAttemptService paymentAttemptService;
    @Autowired private PaymentNotificationIngress paymentNotificationIngress;
    @Autowired private MockPaymentGateway mockPaymentGateway;
    @Autowired private ScheduleMapper scheduleMapper;
    @Autowired private DoctorMapper doctorMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private PatientService patientService;

    @Test
    void paymentAndCancellationProduceOneConsistentTerminalState() throws Exception {
        Long userId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);

        List<Object> outcomes = race(
                () -> formalPayments.pay(userId, created.getPaymentOrderId()),
                () -> cancel(userId, created.getAppointmentOrderId()));

        assertThat(outcomes).hasSize(2);
        outcomes.stream().filter(BusinessException.class::isInstance).map(BusinessException.class::cast)
                .forEach(error -> assertThat(error.getErrorCode()).isEqualTo(com.qsx.appoint.common.ErrorCode.ORDER_STATE_ERROR));
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        PaymentOrder payment = paymentOrderMapper.selectById(created.getPaymentOrderId());
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if ("CONFIRMED".equals(appointment.getStatus())) {
            assertThat(payment.getStatus()).isEqualTo("PAID");
            assertInventory(schedule, 1, 0, 0, 1);
        } else {
            assertThat(appointment.getStatus()).isEqualTo("CANCELLED");
            assertThat(payment.getStatus()).isIn("CLOSED", "REFUND_PENDING", "REFUNDED");
            assertInventory(schedule, 1, 1, 0, 0);
            if ("REFUND_PENDING".equals(payment.getStatus())) {
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_refunds WHERE payment_order_id=?",Integer.class,payment.getId())).isEqualTo(1);
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=? AND event_type='RefundRequested'",Integer.class,payment.getId())).isEqualTo(1);
            }
        }
    }

    @Test
    void concurrentDuplicatePaymentsReturnTheSameSuccess() throws Exception {
        Long userId = createUser();
        CreateAppointmentResponse created = createAppointment(userId, createSchedule());

        paymentAttemptService.startAttempt(userId,created.getPaymentOrderId(),PaymentChannel.MOCK);
        List<Object> outcomes = race(
                () -> formalPayments.pay(userId, created.getPaymentOrderId()),
                () -> formalPayments.pay(userId, created.getPaymentOrderId()));

        assertThat(outcomes).noneMatch(BusinessException.class::isInstance);
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getStatus()).isEqualTo("CONFIRMED");
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getStatus()).isEqualTo("PAID");
        AppointmentOrder confirmed=appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        assertThat(confirmed.getVisitNumber()).isEqualTo(1);
        assertThat(scheduleMapper.selectById(confirmed.getScheduleId()).getNextVisitNumber()).isEqualTo(2);
        assertInventory(scheduleMapper.selectById(confirmed.getScheduleId()),1,0,0,1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_outbox_events WHERE aggregate_id=? AND event_type='PaymentSucceeded'",Integer.class,created.getPaymentOrderId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coordination_inbox_events i JOIN payment_outbox_events o ON o.event_id=i.event_id WHERE o.aggregate_id=? AND o.event_type='PaymentSucceeded'",Integer.class,created.getPaymentOrderId())).isEqualTo(1);
    }

    @Test
    void sameDaySqlAdmissionUsesStrictApplicationTimeThreshold() {
        Long id = createSchedule(1);
        var schedule = scheduleMapper.selectById(id);
        var today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        schedule.setAppointmentDate(today);
        schedule.setSessionType("AFTERNOON_FIRST");
        schedule.setStartTime(LocalTime.of(14, 0));
        schedule.setEndTime(LocalTime.of(16, 0));
        scheduleMapper.updateById(schedule);
        assertThat(scheduleMapper.holdOneBookableSession(id, today, today.plusDays(2), today,
                LocalTime.of(14, 0))).isZero();
        assertThat(scheduleMapper.holdOneBookableSession(id, today.plusDays(1), today.plusDays(2), today,
                LocalTime.of(13, 59, 59))).isZero();
        assertThat(scheduleMapper.holdOneBookableSession(id, today, today.plusDays(2), today,
                LocalTime.of(13, 59, 59))).isEqualTo(1);
        assertInventory(scheduleMapper.selectById(id), 1, 0, 1, 0);
    }

    @Test
    void concurrentFirstBookingCreatesOneSessionAndNeverOversellsDefaultCapacity() throws Exception {
        LocalDate appointmentDate = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(2);
        int contenders = 21;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = new TestExecutor(contenders)) {
            List<Future<Object>> futures = IntStream.range(0, contenders)
                    .mapToObj(index -> {
                        Long userId = createUser();
                        Long patientId = createPatient(userId, "DOC-ON-DEMAND-" + index);
                        return executor.submit(awaitStart(() -> createOnDemandAppointment(
                                userId, patientId, appointmentDate), ready, start));
                    })
                    .toList();
            if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("AppointmentLifecycleMySqlIT: ready屏障超时");
            start.countDown();
            List<Object> outcomes = new java.util.ArrayList<>();
            for (Future<Object> future : futures) outcomes.add(future.get(15, java.util.concurrent.TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(20);
            assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(1);
        }

        Schedule schedule = scheduleMapper.selectByDoctorDateSession(
                2L, appointmentDate, "MORNING_SECOND");
        assertThat(schedule).isNotNull();
        assertInventory(schedule, 20, 0, 20, 0);
        assertThat(scheduleMapper.selectList(null).stream()
                .filter(item -> item.getDoctorId().equals(2L))
                .filter(item -> item.getAppointmentDate().equals(appointmentDate))
                .filter(item -> "MORNING_SECOND".equals(item.getSessionType())))
                .hasSize(1);
    }

    @Test
    void twentyUsersCompetingForLastThreePlacesNeverOversell() throws Exception {
        Long scheduleId = createSchedule(3);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = new TestExecutor(20)) {
            List<Future<Object>> futures = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("AppointmentLifecycleMySqlIT: start屏障超时");
                        Long userId = createUser();
                        Long patientId = createPatient(userId, "990000%012d".formatted(index));
                        try {
                            return createAppointment(userId, patientId, scheduleId, UUID.randomUUID().toString());
                        } catch (BusinessException exception) {
                            return exception;
                        }
                    }))
                    .toList();
            if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("AppointmentLifecycleMySqlIT: ready屏障超时");
            start.countDown();
            List<Object> outcomes = new java.util.ArrayList<>();
            for (Future<Object> future : futures) outcomes.add(future.get(15, java.util.concurrent.TimeUnit.SECONDS));

            assertThat(outcomes.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(3);
            assertThat(outcomes.stream().filter(BusinessException.class::isInstance).count()).isEqualTo(17);
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        assertInventory(schedule, 3, 0, 3, 0);
        assertThat(appointmentOrderMapper.selectList(null).stream()
                .filter(order -> scheduleId.equals(order.getScheduleId()))
                .filter(order -> "PENDING_PAYMENT".equals(order.getStatus())))
                .hasSize(3);
    }

    @Test
    void paymentRacingExpirationProducesOneExpiredTerminalStateAfterDeadline() throws Exception {
        Long userId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        appointment.setPaymentExpiresAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        appointmentOrderMapper.updateById(appointment);
        PaymentOrder expiredPayment = paymentOrderMapper.selectById(created.getPaymentOrderId());
        expiredPayment.setExpiresAt(appointment.getPaymentExpiresAt());
        paymentOrderMapper.updateById(expiredPayment);

        List<Object> outcomes = race(
                () -> formalPayments.pay(userId, created.getPaymentOrderId()),
                () -> lifecycleService.expireIfDue(created.getAppointmentOrderId()));

        assertThat(outcomes).anyMatch(value -> Boolean.TRUE.equals(value));
        assertThat(outcomes).anyMatch(BusinessException.class::isInstance);
        BusinessException rejected = (BusinessException) outcomes.stream()
                .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
        assertThat(rejected.getErrorCode()).isEqualTo(com.qsx.appoint.common.ErrorCode.PAYMENT_EXPIRED);
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getStatus()).isEqualTo("EXPIRED");
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getStatus()).isEqualTo("CLOSED");
        assertInventory(scheduleMapper.selectById(scheduleId), 1, 1, 0, 0);
    }

    @Test
    void callbackRacingCancellationConvergesToOneRefundAndReleasedInventory() throws Exception {
        Long userId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);
        var callback = successfulCallback(userId, created.getPaymentOrderId());

        race(
                () -> paymentNotificationIngress.handle(PaymentChannel.MOCK, callback),
                () -> cancel(userId, created.getAppointmentOrderId()));

        assertClosedWithSingleRefund(created, scheduleId, "CANCELLED");
    }

    @Test
    void callbackRacingExpirationConvergesToOneRefundAndReleasedInventory() throws Exception {
        Long userId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);
        var callback = successfulCallback(userId, created.getPaymentOrderId());
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        appointment.setPaymentExpiresAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        appointmentOrderMapper.updateById(appointment);

        race(
                () -> paymentNotificationIngress.handle(PaymentChannel.MOCK, callback),
                () -> lifecycleService.expireIfDue(created.getAppointmentOrderId()));

        assertClosedWithSingleRefund(created, scheduleId, "EXPIRED");
    }

    @Test
    void concurrentDuplicateCancellationCreatesOnlyOneRefund() throws Exception {
        Long userId = createUser();
        Long scheduleId = createSchedule();
        CreateAppointmentResponse created = createAppointment(userId, scheduleId);
        formalPayments.pay(userId, created.getPaymentOrderId());

        List<Object> outcomes = race(
                () -> cancel(userId, created.getAppointmentOrderId()),
                () -> cancel(userId, created.getAppointmentOrderId()));

        assertThat(outcomes).noneMatch(BusinessException.class::isInstance);
        assertClosedWithSingleRefund(created, scheduleId, "CANCELLED");
    }

    private Object cancel(Long userId, Long appointmentOrderId) {
        lifecycleService.cancelOwned(userId, appointmentOrderId);
        return appointmentService.getOwned(userId, appointmentOrderId);
    }

    private com.qsx.appoint.payment.port.RawPaymentNotification successfulCallback(Long userId, Long paymentId) {
        PaymentAttemptResponse attempt = paymentAttemptService.startAttempt(userId, paymentId, PaymentChannel.MOCK);
        return mockPaymentGateway.complete(attempt.providerOrderNo(), "race-" + UUID.randomUUID(),
                LocalDateTime.now());
    }

    private void assertClosedWithSingleRefund(CreateAppointmentResponse created, Long scheduleId,
                                              String appointmentStatus) {
        assertThat(appointmentOrderMapper.selectById(created.getAppointmentOrderId()).getStatus())
                .isEqualTo(appointmentStatus);
        assertThat(paymentOrderMapper.selectById(created.getPaymentOrderId()).getStatus())
                .isEqualTo("REFUND_PENDING");
        assertThat(paymentRefundMapper.selectList(null).stream()
                .filter(refund -> created.getPaymentOrderId().equals(refund.getPaymentOrderId())))
                .hasSize(1);
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        assertThat(schedule.getHeldCapacity()).isZero();
        assertThat(schedule.getConfirmedCapacity()).isZero();
        assertThat(schedule.getAvailableCapacity()).isEqualTo(schedule.getTotalCapacity());
    }

    @Test
    void concurrentSameIdempotencyKeyReplaysOneCommittedResponse() throws Exception {
        Long userId = createUser();
        Long patientId = createPatient(userId);
        Long scheduleId = createSchedule();
        String key = UUID.randomUUID().toString();

        List<Object> outcomes = race(
                () -> createAppointmentResult(userId, patientId, scheduleId, key),
                () -> createAppointmentResult(userId, patientId, scheduleId, key));

        CreateAppointmentResponse created = (CreateAppointmentResponse) outcomes.stream()
                .filter(CreateAppointmentResponse.class::isInstance).findFirst().orElseThrow();
        ReplayedAppointmentResponse replayed = (ReplayedAppointmentResponse) outcomes.stream()
                .filter(ReplayedAppointmentResponse.class::isInstance).findFirst().orElseThrow();
        AppointmentOrder appointment = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        assertThat(replayed.getAppointmentOrderId()).isEqualTo(created.getAppointmentOrderId());
        assertThat(replayed.getAppointmentNo()).isEqualTo(appointment.getAppointmentNo());
        assertThat(replayed.isReplayed()).isTrue();
        assertThat(appointmentOrderMapper.selectList(null)).filteredOn(order -> order.getUserId().equals(userId))
                .hasSize(1);
    }

    @Test
    void concurrentDifferentPayloadsWithSameKeyYieldOneConflict() throws Exception {
        Long userId = createUser();
        Long patientId = createPatient(userId);
        Long firstScheduleId = createSchedule();
        Long secondScheduleId = createSchedule();
        String key = UUID.randomUUID().toString();

        List<Object> outcomes = race(
                () -> createAppointmentResult(userId, patientId, firstScheduleId, key),
                () -> createAppointmentResult(userId, patientId, secondScheduleId, key));

        assertThat(outcomes.stream().filter(CreateAppointmentResponse.class::isInstance).count()).isEqualTo(1);
        BusinessException conflict = (BusinessException) outcomes.stream()
                .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
        assertThat(conflict.getErrorCode()).isEqualTo(com.qsx.appoint.common.ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void concurrentDuplicatePatientDocumentIsRejectedByDatabaseConstraint() throws Exception {
        Long userId = createUser();
        CreatePatientRequest request = patientRequest("并发就诊人");

        List<Object> outcomes = race(
                () -> patientService.create(userId, request),
                () -> patientService.create(userId, request));

        assertThat(outcomes.stream().filter(PatientResponse.class::isInstance).count()).isEqualTo(1);
        BusinessException duplicate = (BusinessException) outcomes.stream()
                .filter(BusinessException.class::isInstance).findFirst().orElseThrow();
        assertThat(duplicate.getErrorCode()).isEqualTo(com.qsx.appoint.common.ErrorCode.PATIENT_DOCUMENT_EXISTS);
    }

    @Test
    void patientUpdateAndAppointmentCreationSerializeToACompleteSnapshot() throws Exception {
        Long userId = createUser();
        Long patientId = createPatient(userId);
        Long scheduleId = createSchedule();
        UpdatePatientRequest update = new UpdatePatientRequest();
        update.setName("修改后姓名");

        List<Object> outcomes = race(
                () -> createAppointment(userId, patientId, scheduleId, UUID.randomUUID().toString()),
                () -> patientService.updateOwned(userId, patientId, update));

        assertThat(outcomes).anyMatch(CreateAppointmentResponse.class::isInstance);
        assertThat(outcomes).anyMatch(PatientResponse.class::isInstance);
        CreateAppointmentResponse created = (CreateAppointmentResponse) outcomes.stream()
                .filter(CreateAppointmentResponse.class::isInstance).findFirst().orElseThrow();
        AppointmentOrder snapshot = appointmentOrderMapper.selectById(created.getAppointmentOrderId());
        assertThat(snapshot.getPatientName()).isIn("MySQL 就诊人", "修改后姓名");
        assertThat(snapshot.getPatientDocumentNo()).isEqualTo("110105199001010010");
        assertThat(snapshot.getPatientContactPhone()).isEqualTo("13800138000");
        assertThat(patientMapper.selectById(patientId).getName()).isEqualTo("修改后姓名");
    }

    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = new TestExecutor(2)) {
            Future<Object> firstResult = executor.submit(awaitStart(first, ready, start));
            Future<Object> secondResult = executor.submit(awaitStart(second, ready, start));
            if (!ready.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("AppointmentLifecycleMySqlIT: ready屏障超时");
            start.countDown();
            return List.of(firstResult.get(15, java.util.concurrent.TimeUnit.SECONDS), secondResult.get(15, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private Callable<Object> awaitStart(Callable<Object> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("AppointmentLifecycleMySqlIT: start屏障超时");
            try {
                return action.call();
            } catch (BusinessException exception) {
                return exception;
            }
        };
    }

    private Long createUser() {
        User user = new User();
        user.setEmail("mysql-it-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("unused-in-service-test");
        userMapper.insert(user);
        return user.getId();
    }

    private Long createSchedule() {
        return createSchedule(1);
    }

    private Long createSchedule(int capacity) {
        int sequence = SCHEDULE_SEQUENCE.getAndIncrement();
        Schedule schedule = new Schedule();
        schedule.setDoctorId(BookingTestDoctors.copyOf(doctorMapper, 1L + (sequence % 4) / 2));
        schedule.setAppointmentDate(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(2));
        boolean morning = sequence % 2 == 0;
        schedule.setSessionType(morning ? "MORNING_FIRST" : "AFTERNOON_FIRST");
        schedule.setStartTime(morning ? LocalTime.of(8, 0) : LocalTime.of(14, 0));
        schedule.setEndTime(morning ? LocalTime.of(10, 0) : LocalTime.of(16, 0));
        schedule.setStatus("OPEN");
        schedule.setTotalCapacity(capacity);
        schedule.setRegistrationFee(new java.math.BigDecimal("50.00"));
        schedule.setFeeCurrency("CNY");
        schedule.setAvailableCapacity(capacity);
        schedule.setHeldCapacity(0);
        schedule.setConfirmedCapacity(0);
        schedule.setNextVisitNumber(1);
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }

    private CreateAppointmentResponse createAppointment(Long userId, Long scheduleId) {
        return createAppointment(userId, createPatient(userId), scheduleId, UUID.randomUUID().toString());
    }

    private CreateAppointmentResponse createAppointment(Long userId, Long patientId, Long scheduleId, String key) {
        AppointmentCreationResult result = createAppointmentResult(userId, patientId, scheduleId, key);
        assertThat(result).isInstanceOf(CreateAppointmentResponse.class);
        return (CreateAppointmentResponse) result;
    }

    private AppointmentCreationResult createAppointmentResult(Long userId, Long patientId, Long scheduleId, String key) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        request.setDoctorId(schedule.getDoctorId());
        request.setAppointmentDate(schedule.getAppointmentDate());
        request.setSessionType(schedule.getSessionType());
        request.setPatientId(patientId);
        return bookingCoordinator.create(userId, key, request);
    }

    private AppointmentCreationResult createOnDemandAppointment(Long userId, Long patientId,
                                                                 LocalDate appointmentDate) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setDoctorId(2L);
        request.setAppointmentDate(appointmentDate);
        request.setSessionType("MORNING_SECOND");
        request.setPatientId(patientId);
        return bookingCoordinator.create(userId, UUID.randomUUID().toString(), request);
    }

    private Long createPatient(Long userId) {
        return createPatient(userId, "110105199001010010");
    }

    private Long createPatient(Long userId, String documentNo) {
        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName("MySQL 就诊人");
        patient.setDocumentType("RESIDENT_ID_CARD");
        patient.setDocumentNo(documentNo);
        patient.setContactPhone("13800138000");
        patient.setContactEmail("patient@example.com");
        patient.setEnabled(true);
        patientMapper.insert(patient);
        return patient.getId();
    }

    private void assertInventory(Schedule schedule, int total, int available, int held, int confirmed) {
        assertThat(schedule.getStatus()).isEqualTo("OPEN");
        assertThat(schedule.getTotalCapacity()).isEqualTo(total);
        assertThat(schedule.getAvailableCapacity()).isEqualTo(available);
        assertThat(schedule.getHeldCapacity()).isEqualTo(held);
        assertThat(schedule.getConfirmedCapacity()).isEqualTo(confirmed);
        assertThat(available + held + confirmed).isEqualTo(total);
    }

    private CreatePatientRequest patientRequest(String name) {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setName(name);
        request.setDocumentType("RESIDENT_ID_CARD");
        request.setDocumentNo("110105199001010010");
        request.setContactPhone("13800138000");
        request.setContactEmail("patient@example.com");
        return request;
    }
}
